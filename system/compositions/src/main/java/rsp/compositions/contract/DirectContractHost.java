package rsp.compositions.contract;

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
 * Descriptor boundary for a contract component.
 *
 * <p>This host does not create or own a separate contract runtime. It supplies
 * descriptor context and lets the bound contract component own its cache,
 * effects, and subscriptions.</p>
 */
public final class DirectContractHost extends Component<ContractDescriptor, Object> {
    private final ContractDescriptor descriptor;
    private final Component<?, ?> content;
    private final Contract contract;
    private final boolean layer;

    public DirectContractHost(ContractDescriptor descriptor, Component<?, ?> content) {
        this(descriptor, content, false);
    }

    public DirectContractHost(ContractDescriptor descriptor, Component<?, ?> content, boolean layer) {
        super(new ComponentType(Objects.requireNonNull(descriptor, "descriptor").contractClass(),
                descriptor.instanceId()));
        this.descriptor = descriptor;
        this.content = Objects.requireNonNull(content, "content");
        if (!(content instanceof Contract boundContract)) {
            throw new IllegalArgumentException("Direct contract binding must implement Contract: "
                    + descriptor.contractClass().getName());
        }
        this.contract = boundContract;
        this.layer = layer;
    }

    @Override
    public ComponentStateSupplier<ContractDescriptor> initStateSupplier() {
        return (_, context) -> {
            if (!contract.isAuthorized(LookupFactory.create(context))) {
                throw new rsp.server.http.AuthorizationException(
                        "Access denied: insufficient permissions for " + descriptor.contractClass().getName());
            }
            return descriptor;
        };
    }

    @Override
    public BiFunction<ComponentContext, ContractDescriptor, ComponentContext> subComponentsContext() {
        return (context, ignored) -> enrich(context);
    }

    @Override
    public ComponentView<ContractDescriptor, Object> componentView() {
        return _ -> _ -> content;
    }

    @Override
    public void onMounted(ComponentSegment<ContractDescriptor> segment,
                          ComponentCompositeKey componentId,
                          ContractDescriptor state,
                          CommandsEnqueue commandsEnqueue,
                          StateUpdater<ContractDescriptor> stateUpdate) {
        stateUpdate.publish(EventKeys.SCENE_TITLE_UPDATED,
                new EventKeys.SceneTitleUpdate(descriptor.instanceId(), contract.title()));
        Scene scene = segment.componentContext().get(ContextKeys.SCENE);
        if (scene != null && scene.routedDescriptor() != null
                && scene.routedDescriptor().instanceId() == descriptor.instanceId()) {
            stateUpdate.publish(EventKeys.PRIMARY_CONTRACT_MOUNTED,
                    new EventKeys.MountedPrimaryContract(descriptor.instanceId(), contract));
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
                .with(ContextKeys.CONTRACT_CLASS, descriptor.contractClass())
                .with(ContextKeys.IS_ACTIVE_CONTRACT, true);
        if (!descriptor.showData().isEmpty()) {
            result = result.with(ContextKeys.SHOW_DATA, descriptor.showData());
        }
        result = contract.enrichContext(result);
        if (!layer) {
            return result;
        }

        String primaryTitle = context.get(ContextKeys.CONTRACT_TITLE);
        String layerTitle = result.get(ContextKeys.CONTRACT_TITLE);
        if (layerTitle != null && !layerTitle.equals(primaryTitle)) {
            result = result.with(ContextKeys.OVERLAY_TITLE, layerTitle);
        }
        return primaryTitle == null ? result : result.with(ContextKeys.CONTRACT_TITLE, primaryTitle);
    }

    private record ComponentType(Class<? extends Contract> contractClass, long instanceId) {}
}
