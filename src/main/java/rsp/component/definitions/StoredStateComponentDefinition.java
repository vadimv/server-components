package rsp.component.definitions;

import rsp.component.*;

import java.util.Map;
import java.util.Objects;

public class StoredStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final ComponentView<S> view;
    private final S initialState;
    private final Map<ComponentCompositeKey, S> stateStore;

    public StoredStateComponentDefinition(final S initialState,
                                          final ComponentView<S> view,
                                          final Map<ComponentCompositeKey, S> stateStore) {
        super(StoredStateComponentDefinition.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
        this.stateStore = Objects.requireNonNull(stateStore);
    }

    public StoredStateComponentDefinition(final Object componentType,
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
    public ComponentUpdatedCallback<S> onComponentUpdatedCallback() {
        return (key,  odlState, state, newState) -> stateStore.put(key, state);
    }

    @Override
    public ComponentMountedCallback<S> onComponentMountedCallback() {
        return (key, state, newState) -> {
        };
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }
}
