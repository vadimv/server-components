package rsp.compositions.block;

import rsp.component.CommandsEnqueue;
import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentContext;
import rsp.component.ComponentSegment;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.StateUpdater;
import rsp.component.definitions.Component;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Descriptor boundary for a block component.
 *
 * <p>This host does not create or own a separate block runtime. It supplies
 * descriptor context and lets the bound block component own its cache,
 * effects, and subscriptions.</p>
 */
public final class DirectBlockHost extends Component<BlockDescriptor, Object> {
    private final BlockDescriptor descriptor;
    private final Block<?, ?> block;
    private final boolean layer;

    public DirectBlockHost(BlockDescriptor descriptor, Block<?, ?> block) {
        this(descriptor, block, false);
    }

    public DirectBlockHost(BlockDescriptor descriptor, Block<?, ?> block, boolean layer) {
        super(new ComponentType(Objects.requireNonNull(descriptor, "descriptor").blockClass(),
                descriptor.instanceId()));
        this.descriptor = descriptor;
        this.block = Objects.requireNonNull(block, "block");
        this.layer = layer;
    }

    @Override
    public ComponentStateSupplier<BlockDescriptor> initStateSupplier() {
        return (_, context) -> {
            ComponentContext blockContext = enrich(context);
            if (!block.isAuthorized(LookupFactory.create(blockContext))) {
                throw new rsp.server.http.AuthorizationException(
                        "Access denied: insufficient permissions for " + descriptor.blockClass().getName());
            }
            return descriptor;
        };
    }

    @Override
    public BiFunction<ComponentContext, BlockDescriptor, ComponentContext> subComponentsContext() {
        return (context, ignored) -> enrich(context);
    }

    @Override
    public ComponentView<BlockDescriptor, Object> componentView() {
        return _ -> _ -> block;
    }

    @Override
    public void onMounted(ComponentSegment<BlockDescriptor> segment,
                          ComponentCompositeKey componentId,
                          BlockDescriptor state,
                          CommandsEnqueue commandsEnqueue,
                          StateUpdater<BlockDescriptor> stateUpdate) {
        stateUpdate.publish(EventKeys.SCENE_TITLE_UPDATED,
                new EventKeys.SceneTitleUpdate(descriptor.instanceId(), block.title()));
        Scene scene = segment.componentContext().get(ContextKeys.SCENE);
        if (scene != null && scene.routedDescriptor() != null
                && scene.routedDescriptor().instanceId() == descriptor.instanceId()) {
            stateUpdate.publish(EventKeys.PRIMARY_BLOCK_MOUNTED,
                    new EventKeys.MountedPrimaryBlock(descriptor.instanceId(), block));
        }
    }

    @Override
    public boolean providesSubscriberBoundary() {
        return false;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    private ComponentContext enrich(ComponentContext context) {
        ComponentContext result = context
                .with(ContextKeys.BLOCK_CLASS, descriptor.blockClass())
                .with(ContextKeys.IS_ACTIVE_BLOCK, true);
        if (!descriptor.showData().isEmpty()) {
            result = result.with(ContextKeys.SHOW_DATA, descriptor.showData());
        }
        result = block.enrichContext(result);
        if (!layer) {
            return result;
        }

        String primaryTitle = context.get(ContextKeys.BLOCK_TITLE);
        String layerTitle = result.get(ContextKeys.BLOCK_TITLE);
        if (layerTitle != null && !layerTitle.equals(primaryTitle)) {
            result = result.with(ContextKeys.OVERLAY_TITLE, layerTitle);
        }
        return primaryTitle == null ? result : result.with(ContextKeys.BLOCK_TITLE, primaryTitle);
    }

    private record ComponentType(Class<? extends Block<?, ?>> blockClass, long instanceId) {}
}
