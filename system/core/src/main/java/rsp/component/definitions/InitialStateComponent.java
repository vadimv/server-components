package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.View;

import java.util.Objects;

/**
 * A component with its fixed state provided on initialization.
 * @param <S> this component's state type
 * @param <I> this component's intent type
 */
public final class InitialStateComponent<S, I> extends Component<S, I> {

    private final ComponentView<S, I> view;
    private final S initialState;

    public InitialStateComponent(final S initialState,
                                 final ComponentView<S, I> view) {
        super(InitialStateComponent.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState, "Initial state must not be null");
    }

    public InitialStateComponent(final S initialState,
                                 final View<S> view) {
        super(InitialStateComponent.class);
        Objects.requireNonNull(view);
        this.view =  __ -> view;
        this.initialState = Objects.requireNonNull(initialState, "Initial state must not be null");
    }

    public InitialStateComponent(final Object componentType,
                                 final S initialState,
                                 final ComponentView<S, I> view) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState, "Initial state must not be null");
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, _) -> initialState;
    }

    @Override
    public ComponentView<S, I> componentView() {
        return view;
    }
}
