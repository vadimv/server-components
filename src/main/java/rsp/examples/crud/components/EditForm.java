package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.state.Cell;
import rsp.examples.crud.state.Row;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.Optional;

import static rsp.dsl.Html.*;

public class EditForm<K> implements Component<Optional<Row<K>>> {

    private final TextInput[] fieldsComponents;

    public EditForm(TextInput... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }


    @Override
    public DocumentPartDefinition render(UseState<Optional<Row<K>>> useState) {
        return div(span("Edit component:" + useState.get().get().key),
                form(of(Arrays.stream(fieldsComponents).map(component ->
                                        div(renderFieldComponent(useState.get().get(), component)))),
                     button(text("OK")),
                               on("submit", c -> {
                                   System.out.println("submit");
                    })));
    }

    private DocumentPartDefinition renderFieldComponent(Row row, FieldComponent component) {
        return component instanceof EditButton ? component.render(useState(() -> new Cell("rowKey", row.key)))
                : component.render(useState(() -> FieldComponent.cellForComponent(row.cells, component)));
    }
}
