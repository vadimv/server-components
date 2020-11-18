package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Objects;
import java.util.function.Function;

import static rsp.dsl.Html.text;

public class TextField<S> implements FieldComponent<S> {
    private final String fieldName;
    private final Function<S, String> conversion;

    public TextField(String fieldName, Function<S, String> conversion) {
        this.fieldName = Objects.requireNonNull(fieldName);
        this.conversion = Objects.requireNonNull(conversion);
    }

    @Override
    public DocumentPartDefinition render(UseState<S> useState) {
        return text(conversion.apply(useState.get()));
    }


    @Override
    public String key() {
        return fieldName;
    }


}
