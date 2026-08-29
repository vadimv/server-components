package rsp.compositions.block;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.layout.LayerLayout;
import rsp.compositions.routing.AutoAddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

import java.util.Objects;

import static rsp.compositions.block.ActionBindings.ShowPayload;
import static rsp.compositions.block.EventKeys.*;
import static rsp.compositions.routing.AutoAddressBarSyncComponent.PathUpdateMode.RE_RENDER_SUBTREE;
import static rsp.dsl.Html.*;

/**
 * LayerComponent — a scene layer that manages a single active block independently.
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
     * @param descriptor Active block descriptor (null if layer is empty)
     */
    record LayerState(BlockDescriptor descriptor) {
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
                if (scene != null && scene.hasPreActivatedBlocks()) {
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
            Object blockKey = state.descriptor().blockKey();
            Class<? extends Block<?, ?>> blockClass = state.descriptor().blockClass();
            Component<?, ?> bounded = new DirectBlockHost(
                    state.descriptor(), scene.blocks().resolveBlock(blockKey), true);
            Lookup lookup = LookupFactory.create(context);
            return div(
                    layout.resolve(bounded, blockKey, blockClass, lookup),
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

        subscriber.addEventHandler(HIDE, (eventName, blockClass) -> {
            handleHide(state, blockClass, stateUpdate);
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
        Object blockKey = payload.blockKey();
        var data = payload.data();

        // Already active? Let the child layer handle the new SHOW_LAYER.
        if (state.isActive()) {
            return;
        }

        // Validate that this descriptor is backed by the composition.
        ComponentContext context = activeContext();
        Scene scene = context.get(ContextKeys.SCENE);
        if (scene == null) return;

        if (!scene.blocks().hasBinding(blockKey)) return;

        BlockDescriptor descriptor = BlockDescriptor.forTarget(scene.blocks().target(blockKey), data);

        stateUpdate.applyStateTransformation(s -> new LayerState(descriptor));
    }

    private void handleHide(LayerState state,
                            Object blockKey,
                            StateUpdater<LayerState> stateUpdate) {
        if (!state.isActive() || !state.descriptor().blockKey().equals(blockKey)) {
            return;
        }
        stateUpdate.applyStateTransformation(s -> LayerState.EMPTY);
    }

    private void handleActionSuccess(LayerState state,
                                     ActionResult result,
                                     CommandsEnqueue commandsEnqueue) {
        if (!state.isActive()) return;

        Object blockKey = result.blockKey();
        if (!state.descriptor().blockKey().equals(blockKey)) return;

        // Check for auto-open case (URL-routed overlay)
        ComponentContext context = activeContext();
        Scene scene = context.get(ContextKeys.SCENE);
        if (scene != null && scene.autoOpen() != null
                && scene.autoOpen().blockKey().equals(blockKey)) {
            Lookup lookup = LookupFactory.create(context, commandsEnqueue);
            RelativeUrl parentUrl = RouteUtils.buildParentRoute(scene.autoOpen().routePattern(), lookup);
            lookup.publish(AutoAddressBarSyncComponent.SET_PATH,
                    new AutoAddressBarSyncComponent.PathUpdate(parentUrl, RE_RENDER_SUBTREE));
            return;
        }

        // Normal case: publish HIDE
        Lookup lookup = LookupFactory.create(context, commandsEnqueue);
        lookup.publish(HIDE, blockKey);
    }

    private ComponentContext activeContext() {
        if (activeContextScope == null) {
            throw new IllegalStateException("LayerComponent has no live context scope");
        }
        return activeContextScope.current();
    }
}
