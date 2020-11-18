package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.useState;

public class InitialValue<T> implements FieldComponent<T> {

    private final FieldComponent<T> fieldComponent;
    private final T initialState;

    public InitialValue(FieldComponent<T> fieldComponent, T initialState) {
        this.fieldComponent = fieldComponent;
        this.initialState = initialState;
    }

    @Override
    public String key() {
        return fieldComponent.key();
    }

    @Override
    public DocumentPartDefinition render(UseState<T> us) {
        return fieldComponent.render(useState(() -> initialState, v -> us.accept(v)));
    }
}
