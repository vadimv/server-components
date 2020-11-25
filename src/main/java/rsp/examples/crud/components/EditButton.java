package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.a;

public class EditButton implements Component<String> {

    private final String key;
    public EditButton(String key) {
        this.key = key;
    }

    @Override
    public DocumentPartDefinition render(UseState<String> useState) {
        return a("#" + key, "Edit");
    }

}
