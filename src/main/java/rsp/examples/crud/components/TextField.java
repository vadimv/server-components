package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Objects;
import java.util.function.Function;

import static rsp.dsl.Html.text;

public class TextField<S> implements FieldComponent<S> {
    private final String fieldName;

    public TextField(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName);
    }

    @Override
    public DocumentPartDefinition render(UseState<S> useState) {
        return text(useState.get().toString());
    }

    @Override
    public String key() {
        return fieldName;
    }

}
