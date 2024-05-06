package rsp.component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, httpStateOrigin) -> {
            if (stateStore.containsKey(key)) {
                return CompletableFuture.completedFuture(stateStore.get(key));
            } else {
                stateStore.put(key, initialState);
                return CompletableFuture.completedFuture(initialState);
            }
        };
    }

    @Override
    protected ComponentUpdatedCallback<S> componentDidUpdate() {
        return (key, oldState, state, newState) -> stateStore.put(key, state);
    }

    @Override
    protected ComponentView<S> componentView() {
        return view;
    }
}
