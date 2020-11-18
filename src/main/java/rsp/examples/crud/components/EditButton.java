package rsp.examples.crud.components;

import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.a;

public class EditButton implements FieldComponent<String> {

    @Override
    public DocumentPartDefinition render(UseState<String> useState) {
        return a("#" + useState.get(), "Edit");
    }

    @Override
    public String key() {
        return FieldComponent.KEY_FIELD_NAME;
    }
}
