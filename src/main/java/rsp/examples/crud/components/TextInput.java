package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput<T> implements InputComponent<String, T> {

    public final String fieldName;
    private final Optional<String> initialValue;
    private final Function<String, T> conversion;

    public TextInput(String fieldName, String initialValue, Function<String, T> conversion) {
        this.fieldName = fieldName;
        this.initialValue = Optional.of(initialValue);
        this.conversion = conversion;
    }

    public TextInput(String fieldName, Function<String, T> conversion) {
        this.fieldName = fieldName;
        this.initialValue = Optional.empty();
        this.conversion = conversion;
    }

    @Override
    public DocumentPartDefinition render(UseState<T> useState) {
        return div(
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", initialValue.orElse(useState.get().toString()))));
    }

    @Override
    public String key() {
        return fieldName;
    }

    @Override
    public Function<String, T> conversionFrom() {
        return conversion;
    }

    @Override
    public Function<String, Optional<String>>[] validations() {
        return new Function[0];
    }
}
