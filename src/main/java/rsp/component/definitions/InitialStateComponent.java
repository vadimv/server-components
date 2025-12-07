package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.View;

import java.util.Objects;

/**
 * A component with its fixed state provided on an initialization.
 * @param <S>
 */
public class InitialStateComponent<S> extends StatefulComponent<S> {

    private final ComponentView<S> view;
    private final S initialState;

    public InitialStateComponent(final S initialState,
                                 final ComponentView<S> view) {
        super(InitialStateComponent.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    public InitialStateComponent(final S initialState,
                                 final View<S> view) {
        super(InitialStateComponent.class);
        Objects.requireNonNull(view);
        this.view =  __ -> view;
        this.initialState = Objects.requireNonNull(initialState);
    }

    public InitialStateComponent(final Object componentType,
                                 final S initialState,
                                 final ComponentView<S> view) {
        super(componentType);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    @Override
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, _) -> initialState;
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }
}
