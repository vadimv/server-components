package rsp.compositions.contract;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.TreeBuilderFactory;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;

import java.util.Objects;

/**
 * Marks the rendered boundary of one contract view.
 * <p>
 * This component exists to connect two lifecycles that intentionally live at
 * different levels:
 * <ul>
 *     <li>the rendered component subtree, which receives a freshly enriched
 *         {@link ComponentContext} on each render/reconciliation pass, and</li>
 *     <li>the long-lived {@link ViewContract}, owned by {@link ContractRuntime},
 *         which may subscribe to context changes through its scope-backed lookup.</li>
 * </ul>
 * The boundary mirrors the final context it receives into the matching
 * {@link ContractRuntime}, so contract-level {@code lookup.watch(...)}
 * subscriptions observe the same context seen by the rendered view boundary.
 * This is what lets contracts react to sibling-provided context values without
 * depending on {@link Scene} as a shared mutable state source.
 * <h2>Typical Use Cases</h2>
 * Use a boundary whenever a rendered view belongs to a {@link ViewContract}
 * whose runtime should observe the final context visible at that view boundary.
 * Common examples include:
 * <ul>
 *     <li>Wrapping the routed primary view in a layout, so the active contract
 *         can watch route, query, auth, or layout-provided context changes.</li>
 *     <li>Wrapping companion views such as headers, explorers, prompts, sidebars,
 *         or tool panels, so their contracts can observe context produced by the
 *         primary contract or by other companions.</li>
 *     <li>Wrapping overlay or modal contract views created by {@link LayerComponent},
 *         so the overlay contract observes show data, active-contract flags, and
 *         the enriched overlay context.</li>
 *     <li>Supporting sibling state propagation, for example an explorer contract
 *         writing the primary category into context while a prompt contract watches
 *         that key to keep its own contract-side state synchronized.</li>
 *     <li>Keeping long-lived contract subscriptions valid across component
 *         reconciliation, while allowing the rendered component subtree to be
 *         recreated or reused independently.</li>
 * </ul>
 * Do not use this component as a general-purpose wrapper around ordinary
 * components. It is specifically for the contract/view boundary where a
 * {@link ContractRuntime} owns a long-lived contract instance.
 * <p>
 * The boundary deliberately does not call {@link ViewContract#enrichContext}.
 * Context enrichment remains owned by {@link SceneContextEnricher} for the base
 * layer and {@link LayerComponent#subComponentsContext()} for overlays. This
 * component only records the already-final boundary context for the contract
 * runtime.
 * <p>
 * It is also transparent for component-event subscribers:
 * {@link #providesSubscriberBoundary()} returns {@code false}, so the wrapped
 * view keeps using the upstream event boundary. It is reusable because its state
 * is just the stable {@link ContractRuntime}, while fresh context is delivered
 * through the mirrored {@link rsp.component.ContextScope}.
 */
public final class ContractBoundaryComponent extends Component<ContractRuntime> {
    private final ContractRuntime runtime;
    private final Component<?> content;

    public ContractBoundaryComponent(final ContractRuntime runtime,
                                     final Component<?> content) {
        super(new ComponentType(Objects.requireNonNull(runtime, "runtime").contractClass(), runtime));
        this.runtime = runtime;
        this.content = Objects.requireNonNull(content, "content");
    }

    @Override
    public ComponentStateSupplier<ContractRuntime> initStateSupplier() {
        return (_, context) -> {
            runtime.replaceContext(context);
            return runtime;
        };
    }

    @Override
    public ComponentSegment<ContractRuntime> createComponentSegment(final QualifiedSessionId sessionId,
                                                                    final TreePositionPath componentPath,
                                                                    final TreeBuilderFactory treeBuilderFactory,
                                                                    final ComponentContext componentContext,
                                                                    final CommandsEnqueue commandsEnqueue) {
        final ComponentSegment<ContractRuntime> segment = super.createComponentSegment(
                sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
        segment.mirrorContextTo(runtime.contextController());
        return segment;
    }

    @Override
    public rsp.component.ComponentView<ContractRuntime> componentView() {
        return _ -> _ -> content;
    }

    @Override
    public boolean providesSubscriberBoundary() {
        return false;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    private record ComponentType(Class<? extends ViewContract> contractClass,
                                 ContractRuntime runtime) {}
}
