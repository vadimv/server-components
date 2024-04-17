package rsp.component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class InitialStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final ComponentView<S> view;
    private final S initialState;

    public InitialStateComponentDefinition(final S initialState,
                                           final ComponentView<S> view) {
        super(InitialStateComponentDefinition.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    public InitialStateComponentDefinition(final Object componentType,
                                           final S initialState,
                                           final ComponentView<S> view) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, httpStateOrigin) -> CompletableFuture.completedFuture(initialState);
    }

    @Override
    protected ComponentView<S> componentView() {
        return view;
    }

    @Override
    protected StateAppliedCallback<S> componentDidUpdate() {
        return (key, state, componentRenderContext) -> {
            // NO-OP
        };
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
