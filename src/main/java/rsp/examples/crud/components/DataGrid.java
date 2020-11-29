package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static rsp.dsl.Html.*;
import static rsp.state.UseState.useState;

public class DataGrid<T> implements Component<DataGrid.Table<String, T>> {

    private final Column<T>[] columns;

    public DataGrid(Column<T>... columns) {

        this.columns = columns;
    }

    @Override
    public DocumentPartDefinition render(UseState<DataGrid.Table<String, T>> state) {
        return div(
                table(
                        thead(tr(th(""), of(Arrays.stream(columns).map(h -> th(h.title))))),
                        tbody(
                                of(Arrays.stream(state.get().rows).map(row -> tr(
                                        td(input(attr("type", "checkbox"),
                                                 when(state.get().selectedRows.contains(row), () -> attr("checked")),
                                                 attr("autocomplete", "off"),
                                                 on("click", ctx -> state.accept(state.get().toggleRowSelection(row))))),
                                        of(Arrays.stream(columns).map(column -> td(column.fieldComponent.apply(row.key, row.data).render(useState()))))
                                )))))
                );
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
    public static class Header {
        private final String[] columnsHeaders;

        public Header(String... columnsHeaders) {
            this.columnsHeaders = columnsHeaders;
        }
    }
}
