package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.examples.crud.state.Row;
import rsp.examples.crud.state.Table;
import rsp.state.UseState;

import java.util.Arrays;

import static rsp.dsl.Html.*;

public class Grid<K, T> implements Component<Table<K, T>> {

    private final FieldComponent<?>[] fieldsComponents;

    public Grid(FieldComponent<?>... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }

    @Override
    public DocumentPartDefinition render(UseState<Table<K, T>> state) {
        return div(
                table(
                        tbody(
                                Html.of(Arrays.stream(state.get().rows).map(row -> tr(
                                        td(input(attr("type", "checkbox"),
                                                 when(state.get().selectedRows.contains(row), () -> attr("checked")),
                                                 attr("autocomplete", "off"),
                                                 on("click", ctx -> state.accept(state.get().toggleRowSelection(row))))),
                                        of(Arrays.stream(fieldsComponents).map(component ->
                                                td(renderFieldComponent(row, component))

                                        )))
                                )))));
    }

    private DocumentPartDefinition renderFieldComponent(Row<K, T> row, FieldComponent component) {
        return component.render(useState(() -> FieldComponent.dataForComponent(row, component)));
    }


}
