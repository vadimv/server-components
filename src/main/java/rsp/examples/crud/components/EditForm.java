package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;

import java.util.Optional;

import static rsp.dsl.Html.*;

public class EditForm<K> implements Component<Optional<Row<K>>> {

    public EditForm() {
    }


    @Override
    public DocumentPartDefinition render(UseState<Optional<Row<K>>> useState) {
        return div(span("Edit component:" + useState.get().get().key),
                form(input(attr("type", "text"),
                           attr("placeholder", "value1")),
                     button(text("OK")),
                               on("submit", c -> {
                                   System.out.println("submit");
                    })));
    }
}
