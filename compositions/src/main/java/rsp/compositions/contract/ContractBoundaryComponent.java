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
 * The component does not enrich context itself. It mirrors the final context it
 * receives into the matching {@link ContractRuntime}, so contract-level
 * {@code lookup.watch(...)} subscriptions observe the same context seen by the
 * rendered view boundary.
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

    private record ComponentType(Class<? extends ViewContract> contractClass,
                                 ContractRuntime runtime) {}
}
