package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.LayerLayout;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;
import static rsp.dsl.Html.*;

/**
 * LayerComponent — a scene layer that manages a single active contract independently.
 * <p>
 * By handling SHOW/HIDE events in its own component, the base layer
 * (routed + companions) is never re-rendered when layers open/close.
 * <p>
 * Layers stack recursively: when this layer is active, it renders a child
 * LayerComponent for the next level. A new SHOW event while this layer is
 * already active is ignored here and handled by the child layer.
 * <p>
 * The visual rendering is delegated to a {@link LayerLayout} strategy
 * (e.g., modal overlay, activities overview, side panel).
 * <p>
 * Position in component tree: SceneComponent → [Layout (base layer), LayerComponent]
 */
public class LayerComponent extends Component<LayerComponent.LayerState> {

    /**
     * State for a layer.
     *
     * @param runtime Active contract runtime (null if layer is empty)
     * @param contractClass The contract class (for HIDE matching)
     * @param showData Data passed with the SHOW event
     */
    record LayerState(ContractRuntime runtime,
                      Class<? extends ViewContract> contractClass,
                      Map<String, Object> showData) {
        static final LayerState EMPTY = new LayerState(null, null, Map.of());

        boolean isActive() {
            return runtime != null;
        }
    }

    private final LayerLayout layout;
    private final int level;
    private ComponentContext savedContext;

    public LayerComponent(LayerLayout layout) {
        this(layout, 1);
    }

    private LayerComponent(LayerLayout layout, int level) {
        super("layer-" + level);
        this.layout = Objects.requireNonNull(layout, "layout");
        this.level = level;
    }

    @Override
    public ComponentStateSupplier<LayerState> initStateSupplier() {
        return (_, context) -> {
            this.savedContext = context;
            // Only the first layer picks up auto-opened overlay from Scene
            if (level == 1) {
                Scene scene = context.get(ContextKeys.SCENE);
                if (scene != null && scene.hasPreActivatedContracts()) {
                    var entry = scene.preActivatedRuntimes().entrySet().iterator().next();
                    return new LayerState(entry.getValue(), entry.getKey(), Map.of());
                }
            }
            return LayerState.EMPTY;
        };
    }

    @Override
    public BiFunction<ComponentContext, LayerState, ComponentContext> subComponentsContext() {
        return (context, state) -> {
            this.savedContext = context;
            if (state.isActive()) {
                // Preserve primary title before layer contract enrichment
                String primaryTitle = context.get(ContextKeys.CONTRACT_TITLE);

                ComponentContext activeContext = context
                        .with(ContextKeys.CONTRACT_CLASS, state.contractClass())
                        .with(ContextKeys.IS_ACTIVE_CONTRACT, true);
                if (state.showData() != null && !state.showData().isEmpty()) {
                    activeContext = activeContext.with(ContextKeys.SHOW_DATA, state.showData());
                }

                // Let the contract enrich context for its UI component
                state.runtime().replaceContext(activeContext);
                ComponentContext enriched = state.runtime().contract().enrichContext(activeContext);

                // Store overlay title separately
                String layerTitle = enriched.get(ContextKeys.CONTRACT_TITLE);
                if (layerTitle != null && !layerTitle.equals(primaryTitle)) {
                    enriched = enriched.with(ContextKeys.OVERLAY_TITLE, layerTitle);
                }
                // Restore primary title
                if (primaryTitle != null) {
                    enriched = enriched.with(ContextKeys.CONTRACT_TITLE, primaryTitle);
                }
                return enriched;
            }
            return context;
        };
    }

    @Override
    public ComponentView<LayerState> componentView() {
        return _ -> state -> {
            if (!state.isActive()) {
                // Empty div anchor — required so the component has a DOM path for state updates
                return div();
            }
            Scene scene = savedContext.get(ContextKeys.SCENE);
            if (scene == null) {
                return div();
            }
            Component<?> uiComponent = scene.contracts().resolveView(state.contractClass());
            Component<?> bounded = new ContractBoundaryComponent(state.runtime(), uiComponent);
            Lookup lookup = LookupFactory.create(savedContext);
            return div(
                    layout.resolve(bounded, state.contractClass(), lookup),
                    new LayerComponent(layout, level + 1));
        };
    }

    @Override
    public void onAfterRendered(LayerState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<LayerState> stateUpdate) {
        subscriber.addEventHandler(SHOW, (eventName, payload) -> {
            handleShow(state, payload, stateUpdate, commandsEnqueue);
        }, false);

        subscriber.addEventHandler(HIDE, (eventName, contractClass) -> {
            handleHide(state, contractClass, stateUpdate);
        }, false);

        subscriber.addEventHandler(ACTION_SUCCESS, (eventName, result) -> {
            handleActionSuccess(state, result, commandsEnqueue);
        }, false);
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, LayerState state) {
        if (state != null && state.isActive()) {
            state.runtime().destroy();
        }
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    private void handleShow(LayerState state, ShowPayload payload,
                            StateUpdate<LayerState> stateUpdate,
                            CommandsEnqueue commandsEnqueue) {
        Class<? extends ViewContract> contractClass = payload.contractClass();
        Map<String, Object> data = payload.data();

        // Already active? Let the child layer handle the new SHOW.
        if (state.isActive()) {
            return;
        }

        // Resolve factory from Scene
        Scene scene = savedContext.get(ContextKeys.SCENE);
        if (scene == null) return;

        Function<Lookup, ViewContract> factory = scene.getFactory(contractClass);
        if (factory == null) {
            Composition composition = scene.composition();
            if (composition != null) {
                factory = composition.contracts().contractFactory(contractClass);
            }
        }
        if (factory == null) return;

        // Create lookup with context enrichment
        ComponentContext showContext = savedContext
                .with(ContextKeys.CONTRACT_CLASS, contractClass)
                .with(ContextKeys.IS_ACTIVE_CONTRACT, true)
                .with(ContextKeys.SCENE, scene);

        if (data != null && !data.isEmpty()) {
            showContext = showContext.with(ContextKeys.SHOW_DATA, data);
        }

        ContractRuntime runtime = ContractRuntime.instantiate(contractClass, factory, showContext, commandsEnqueue);
        if (runtime == null) return;

        stateUpdate.applyStateTransformation(s ->
                new LayerState(runtime, contractClass, data != null ? data : Map.of()));
    }

    private void handleHide(LayerState state,
                            Class<? extends ViewContract> contractClass,
                            StateUpdate<LayerState> stateUpdate) {
        if (!state.isActive() || !state.contractClass().equals(contractClass)) {
            return;
        }
        state.runtime().destroy();
        stateUpdate.applyStateTransformation(s -> LayerState.EMPTY);
    }

    private void handleActionSuccess(LayerState state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue) {
        if (!state.isActive()) return;

        Class<? extends ViewContract> contractClass = result.contractClass();
        if (contractClass == null) return;
        if (!state.contractClass().equals(contractClass)) return;

        // Check for auto-open case (URL-routed overlay)
        Scene scene = savedContext.get(ContextKeys.SCENE);
        if (scene != null && scene.autoOpen() != null
                && scene.autoOpen().contractClass().equals(contractClass)) {
            Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
            RelativeUrl parentUrl = RouteUtils.buildParentRoute(scene.autoOpen().routePattern(), lookup);
            lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                    new AutoAddressBarSyncComponent.PathUpdate(parentUrl, RE_RENDER_SUBTREE));
            return;
        }

        // Normal case: publish HIDE
        Lookup lookup = LookupFactory.create(savedContext, commandsEnqueue);
        lookup.publish(HIDE, contractClass);
    }
}
