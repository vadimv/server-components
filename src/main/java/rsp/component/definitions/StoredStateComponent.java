package rsp.component.definitions;

import rsp.component.*;

import java.util.Map;
import java.util.Objects;

/**
 * A component with its state provided on an initialization and stored in a cache.
 * @param <S> this component's state type
 */
public class StoredStateComponent<S> extends Component<S> {

    private final ComponentView<S> view;
    private final S initialState;
    private final Map<ComponentCompositeKey, S> stateStore;

    public StoredStateComponent(final S initialState,
                                final ComponentView<S> view,
                                final Map<ComponentCompositeKey, S> stateStore) {
        super(StoredStateComponent.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    public StoredStateComponent(final Object componentType,
                                final S initialState,
                                final ComponentView<S> view,
                                final Map<ComponentCompositeKey, S> stateStore) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return   (componentKey, _) -> {
            if (stateStore.containsKey(componentKey)) {
                return stateStore.get(componentKey);
            } else {
                stateStore.put(componentKey, initialState);
                return initialState;
            }
        };
    }

    @Override
    public void onComponentUpdated(final ComponentCompositeKey componentId, final S oldState, final S newState, final StateUpdate<S> stateUpdate) {
        stateStore.put(componentId, newState);
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }
}
