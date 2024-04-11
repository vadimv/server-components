package rsp.component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class StoredStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final ComponentView<S> view;
    private final S initialState;
    private final Map<ComponentCompositeKey, S> stateStore;

    public StoredStateComponentDefinition(final Object componentType,
                                          final ComponentView<S> view,
                                          final S initialState,
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
    protected ComponentView<S> componentView() {
        return view;
    }

    @Override
    protected StateAppliedCallback<S> componentDidUpdate() {
        return (key, state, componentRenderContext) -> stateStore.put(key, state);
    }

    @Override
    protected MountCallback<S> componentDidMount() {
        return (key, state, newState, componentRenderContext) -> {
            // NO-OP
        };
    }

    @Override
    protected UnmountCallback<S> componentWillUnmount() {
        return (key, state) -> {
            // NO-OP
        };
    }
}
