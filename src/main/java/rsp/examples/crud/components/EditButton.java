package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Cell;
import rsp.state.UseState;

import static rsp.dsl.Html.a;

public class EditButton implements FieldComponent {

    @Override
    public DocumentPartDefinition render(UseState<Cell> useState) {
        return a("#" + useState.get().data, "Edit");
    }

    @Override
    public String get() {
        return "";
    }
}
