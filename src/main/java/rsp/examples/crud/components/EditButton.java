package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.a;

public class EditButton implements Grid.FieldComponent {

    @Override
    public DocumentPartDefinition of(UseState<Grid.Cell> useState) {
        return a("#" + useState.get().data, "Edit");
    }

    @Override
    public String get() {
        return "";
    }
}
