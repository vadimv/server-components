package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput implements FieldComponent<String> {

    public final String fieldName;
    private final Optional<String> initialValue;

    public TextInput(String fieldName, String initialValue) {
        this.fieldName = fieldName;
        this.initialValue = Optional.of(initialValue);
    }

    public TextInput(String fieldName) {
        this.fieldName = fieldName;
        this.initialValue = Optional.empty();
    }

    @Override
    public DocumentPartDefinition render(UseState<String> useState) {
        return div(
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", initialValue.orElse(useState.get()))));
    }

    @Override
    public String key() {
        return fieldName;
    }
}
