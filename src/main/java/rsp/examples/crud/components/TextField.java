package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import java.util.Objects;

import static rsp.dsl.Html.text;

public class TextField implements Grid.FieldComponent {
    private final String fieldName;

    public TextField(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName);
    }

    @Override
    public DocumentPartDefinition of(UseState<Grid.Cell> useState) {
        return text(useState.get());
    }


    @Override
    public String get() {
        return fieldName;
    }
}
