package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput implements Component<Optional<String>> {

    public final String fieldName;
    private final String label;
    private final String initialValue;
    private final Function<String, Optional<String>>[] validations;

    @SafeVarargs
    public TextInput(String fieldName,
                     String label,
                     String initialValue,
                     Function<String, Optional<String>>... validations) {
        this.fieldName = fieldName;
        this.label = label;
        this.initialValue = initialValue;
        this.validations = validations;
    }

    @Override
    public DocumentPartDefinition render(UseState<Optional<String>> useState) {
        return div(span(label),
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", initialValue)),
                   of(useState.get().stream().map(validationErrorMessage -> span(style("color", "red"),
                                                                                 text(validationErrorMessage)))));
    }

    public Function<String, Optional<String>>[] validations() {
        return validations;
    }
}
