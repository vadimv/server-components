package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.examples.crud.state.Cell;
import rsp.examples.crud.state.Row;
import rsp.examples.crud.state.Table;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.function.Supplier;

import static rsp.dsl.Html.*;

public class Grid<K> implements Component<Table<K>> {

    private final FieldComponent[] fieldsComponents;

    public Grid(FieldComponent... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }

    @Override
    public DocumentPartDefinition render(UseState<Table<K>> state) {
        return div(
                table(
                        tbody(
                                Html.of(Arrays.stream(state.get().rows).map(row -> tr(
                                        td(input(attr("type", "checkbox"),
                                                 when(state.get().selectedRows.contains(row), () -> attr("checked")),
                                                 attr("autocomplete", "off"),
                                                 on("click", ctx -> state.accept(state.get().toggleRowSelection(row))))),
                                        Html.of(Arrays.stream(fieldsComponents).map(component ->
                                                td(renderFieldComponent(row, component))

                                        )))
                                )))));
    }

    private DocumentPartDefinition renderFieldComponent(Row row, FieldComponent component) {
        return component instanceof EditButton ? component.render(useState(() -> new Cell("rowKey", row.key)))
                : component.render(useState(() -> forComponent(row.cells, component)));
    }

    private Cell forComponent(Cell[] cells, FieldComponent fieldComponent) {
        for (Cell cell : cells) {
            if (cell.fieldName.equals(fieldComponent.get())) {
                return cell;
            }
        }
        return new Cell("null", "Field not found");

    }

    public interface FieldComponent extends Component<Cell>, Supplier<String> {}

}
