package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput implements Component<Void> {

    public final String fieldName;
    private final String initialValue;

    public TextInput(String fieldName, String initialValue) {
        this.fieldName = fieldName;
        this.initialValue = initialValue;
    }

    @Override
    public DocumentPartDefinition render(UseState<Void> useState) {
        return div(
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", initialValue)));
    }

    public Function<String, Optional<String>>[] validations() {
        return new Function[0];
    }
}
