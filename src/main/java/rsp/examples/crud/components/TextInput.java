package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Cell;
import rsp.state.UseState;

import java.util.Optional;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class TextInput<T> implements FieldComponent {

    public final String fieldName;
    public final Function<String, T> conversion;
    public final Function<String, Optional<String>>[] validations;

    public TextInput(String fieldName, Function<String, T> conversion, Function<String, Optional<String>>... validations) {
        this.fieldName = fieldName;
        this.conversion = conversion;
        this.validations = validations;
    }

    @Override
    public DocumentPartDefinition render(UseState<Cell> useState) {
        return div(span(useState.get().fieldName + ":"),
                   input(attr("type", "text"),
                         attr("name", fieldName),
                         prop("value", useState.get().data.toString())));
    }

    @Override
    public String get() {
        return fieldName;
    }

    public boolean validate(String str) {
        
        return true;
    }

}
