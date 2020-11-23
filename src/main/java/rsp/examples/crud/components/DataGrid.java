package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static rsp.dsl.Html.*;

public class DataGrid<K, T> implements Component<DataGrid.Table<K, T>> {

    private final FieldComponent<?>[] fieldsComponents;

    public DataGrid(FieldComponent<?>... fieldsComponents) {
        this.fieldsComponents = fieldsComponents;
    }

    @Override
    public DocumentPartDefinition render(UseState<DataGrid.Table<K, T>> state) {
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

    private DocumentPartDefinition renderFieldComponent(KeyedEntity<K, T> row, FieldComponent component) {
        return component.render(useState(() -> FieldComponent.dataForComponent(row, component).get().toString()));
    }

    public static class Table<K, T> {
        public final KeyedEntity<K, T>[] rows;
        public final Set<KeyedEntity<K, T>> selectedRows;

        public Table(KeyedEntity<K, T>[] rows, Set<KeyedEntity<K, T>> selectedRows) {
            this.rows = Objects.requireNonNull(rows);
            this.selectedRows = Objects.requireNonNull(selectedRows);
        }

        public static <K, T> Table<K, T> empty() {
            return new Table<>(new KeyedEntity[] {}, Set.of());
        }

        public Table<K, T> toggleRowSelection(KeyedEntity<K, T> row) {
            final Set<KeyedEntity<K, T>> sr = new HashSet<>(selectedRows);
            if (selectedRows.contains(row)) {
                sr.remove(row);
            } else {
                sr.add(row);
            }
            return new Table<>(rows, sr);
        }
    }
}
