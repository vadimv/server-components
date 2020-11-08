package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Cell;
import rsp.state.UseState;

import static rsp.dsl.Html.*;

public class TextInput<T> implements FieldComponent {

    public final String fieldName;

    public TextInput(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public DocumentPartDefinition render(UseState<Cell> useState) {
        return div(span(useState.get().fieldName + ":"),
                   input(attr("type", "text"),
                         attr("value", useState.get().data.toString())));
    }

    @Override
    public String get() {
        return fieldName;
    }

}
