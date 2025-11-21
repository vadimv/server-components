package rsp.component.definitions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.View;

import java.util.Objects;

public class InitialStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final ComponentView<S> view;
    private final S initialState;

    public InitialStateComponentDefinition(final S initialState,
                                           final ComponentView<S> view) {
        super(InitialStateComponentDefinition.class);
        this.view = Objects.requireNonNull(view);
        this.initialState = Objects.requireNonNull(initialState);
    }

    public InitialStateComponentDefinition(final S initialState,
                                           final View<S> view) {
        super(InitialStateComponentDefinition.class);
        Objects.requireNonNull(view);
        this.view =  __ -> view;
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
    public ComponentStateSupplier<S> initStateSupplier() {
        return (_, _) -> initialState;
    }

    @Override
    public ComponentView<S> componentView() {
        return view;
    }
}
