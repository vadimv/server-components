package rsp.component;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class InitialStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final ComponentView<S> view;
    private final CompletableFuture<S> initialState;

    public InitialStateComponentDefinition(final CompletableFuture<S> initialState,
                                           final ComponentView<S> view) {
        super(InitialStateComponentDefinition.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    public InitialStateComponentDefinition(final S initialState,
                                           final ComponentView<S> view) {
        super(InitialStateComponentDefinition.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = CompletableFuture.completedFuture(Objects.requireNonNull(initialState));
    }

    public InitialStateComponentDefinition(final S initialState,
                                           final View<S> view) {
        super(InitialStateComponentDefinition.class);
        Objects.requireNonNull(view);
        this.view =  __ -> view;
        this.initialState = CompletableFuture.completedFuture(Objects.requireNonNull(initialState));
    }

    public InitialStateComponentDefinition(final Object componentType,
                                           final S initialState,
                                           final ComponentView<S> view) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
        this.initialState = CompletableFuture.completedFuture(Objects.requireNonNull(initialState));
    }

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return key -> initialState;
    }

    @Override
    protected ComponentView<S> componentView() {
        return view;
    }
}
