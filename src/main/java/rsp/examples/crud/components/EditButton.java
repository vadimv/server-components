package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.state.UseState;

import static rsp.dsl.Html.a;
import static rsp.dsl.Html.on;

public class EditButton implements Component<String> {


    public EditButton() {
    }

    @Override
    public DocumentPartDefinition render(UseState<String> useState) {
        return a("#", "Edit", on("click", ctx -> {
            useState.accept(useState.get());
        }));
    }

}
