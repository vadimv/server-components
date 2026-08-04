package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.layout.LayerLayout;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

import java.util.Objects;

import static rsp.compositions.contract.ActionBindings.ShowPayload;
import static rsp.compositions.contract.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;
import static rsp.dsl.Html.*;

/**
 * LayerComponent — a scene layer that manages a single active contract independently.
 * <p>
 * By handling SHOW_LAYER/HIDE events in its own component, the base layer
 * (routed + companions) is never re-rendered when layers open/close.
 * <p>
 * Layers stack recursively: when this layer is active, it renders a child
 * LayerComponent for the next level. A new SHOW_LAYER event while this layer is
 * already active is ignored here and handled by the child layer.
 * <p>
 * The visual rendering is delegated to a {@link LayerLayout} strategy
 * (e.g., modal overlay, activities overview, side panel).
 * <p>
 * Position in component tree: SceneComponent → [Layout (base layer), LayerComponent]
 */
public class LayerComponent extends Component<LayerComponent.LayerState, Object> {

    /**
     * State for a layer.
     *
     * @param descriptor Active contract descriptor (null if layer is empty)
     */
    record LayerState(ContractDescriptor descriptor) {
        static final LayerState EMPTY = new LayerState(null);

        boolean isActive() {
            return descriptor != null;
        }
    }

    private final LayerLayout layout;
    private final int level;
    private ContextScope activeContextScope;

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
            // Only the first layer picks up auto-opened overlay from Scene
            if (level == 1) {
                Scene scene = context.get(ContextKeys.SCENE);
                if (scene != null && scene.hasPreActivatedContracts()) {
                    var entry = scene.preActivatedDescriptors().entrySet().iterator().next();
                    return new LayerState(entry.getValue());
                }
            }
            return LayerState.EMPTY;
        };
    }

    @Override
    public ComponentView<LayerState, Object> componentView() {
        return _ -> state -> {
            if (!state.isActive()) {
                // Empty div anchor — required so the component has a DOM path for state updates
                return div();
            }
            ComponentContext context = activeContext();
            Scene scene = context.get(ContextKeys.SCENE);
            if (scene == null) {
                return div();
            }
            Class<? extends Contract> contractClass = state.descriptor().contractClass();
            Component<?, ?> bounded = new DirectContractHost(
                    state.descriptor(), scene.contracts().resolveBoundComponent(contractClass), true);
            Lookup lookup = LookupFactory.create(context);
            return div(
                    layout.resolve(bounded, contractClass, lookup),
                    new LayerComponent(layout, level + 1));
        };
    }

    @Override
    public void onAfterRendered(LayerState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdater<LayerState> stateUpdate) {
        subscriber.addEventHandler(SHOW_LAYER, (eventName, payload) -> {
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
    public void onBeforeRendered(ComponentSegment<LayerState> segment, LayerState state) {
        activeContextScope = segment.contextScope();
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, LayerState state) {
        activeContextScope = null;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    private void handleShow(LayerState state, ShowPayload payload,
                            StateUpdater<LayerState> stateUpdate,
                            CommandsEnqueue commandsEnqueue) {
        Class<? extends Contract> contractClass = payload.contractClass();
        var data = payload.data();

        // Already active? Let the child layer handle the new SHOW_LAYER.
        if (state.isActive()) {
            return;
        }

        // Validate that this descriptor is backed by the composition.
        ComponentContext context = activeContext();
        Scene scene = context.get(ContextKeys.SCENE);
        if (scene == null) return;

        if (!scene.contracts().hasBinding(contractClass)) return;

        ContractDescriptor descriptor = ContractDescriptor.forContract(contractClass, data);

        stateUpdate.applyStateTransformation(s -> new LayerState(descriptor));
    }

    private void handleHide(LayerState state,
                            Class<? extends Contract> contractClass,
                            StateUpdater<LayerState> stateUpdate) {
        if (!state.isActive() || !state.descriptor().contractClass().equals(contractClass)) {
            return;
        }
        stateUpdate.applyStateTransformation(s -> LayerState.EMPTY);
    }

    private void handleActionSuccess(LayerState state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue) {
        if (!state.isActive()) return;

        Class<? extends Contract> contractClass = result.contractClass();
        if (contractClass == null) return;
        if (!state.descriptor().contractClass().equals(contractClass)) return;

        // Check for auto-open case (URL-routed overlay)
        ComponentContext context = activeContext();
        Scene scene = context.get(ContextKeys.SCENE);
        if (scene != null && scene.autoOpen() != null
                && scene.autoOpen().contractClass().equals(contractClass)) {
            Lookup lookup = LookupFactory.create(context, commandsEnqueue);
            RelativeUrl parentUrl = RouteUtils.buildParentRoute(scene.autoOpen().routePattern(), lookup);
            lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                    new AutoAddressBarSyncComponent.PathUpdate(parentUrl, RE_RENDER_SUBTREE));
            return;
        }

        // Normal case: publish HIDE
        Lookup lookup = LookupFactory.create(context, commandsEnqueue);
        lookup.publish(HIDE, contractClass);
    }

    private ComponentContext activeContext() {
        if (activeContextScope == null) {
            throw new IllegalStateException("LayerComponent has no live context scope");
        }
        return activeContextScope.current();
    }
}
