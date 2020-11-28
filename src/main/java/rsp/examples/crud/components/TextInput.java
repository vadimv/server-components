package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput implements Component<Optional<String>> {

    public final String fieldName;
    private final String initialValue;
    private Function<String, Optional<String>>[] validations;

    public TextInput(String fieldName,
                     String initialValue,
                     Function<String, Optional<String>>... validations) {
        this.fieldName = fieldName;
        this.initialValue = initialValue;
        this.validations = validations;
    }

    @Override
    public DocumentPartDefinition render(UseState<Optional<String>> useState) {
        return div(
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", initialValue)),
                   Html.of(useState.get().stream().map(validationErrorMessage -> span(style("font-color", "red"),
                                                                                      text(validationErrorMessage))))
               );
    }

    public Function<String, Optional<String>>[] validations() {
        return validations;
    }
}
