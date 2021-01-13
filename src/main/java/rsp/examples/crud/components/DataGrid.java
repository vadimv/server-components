package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.state.UseState;

import java.util.*;

import static rsp.dsl.Html.*;

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
                                        of(Arrays.stream(columns).map(column -> td(column.fieldComponent.apply(row.data)
                                                .render(UseState.readWrite(() -> row.key, k -> state.accept(state.get().withEditRowKey(Optional.of(row.key))))))))
                                )))))
                );
    }

    public static class Table<K, T> {
        public final KeyedEntity<K, T>[] rows;
        public final Set<KeyedEntity<K, T>> selectedRows;
        public final Optional<String> editRowKey;

        public Table(KeyedEntity<K, T>[] rows, Set<KeyedEntity<K, T>> selectedRows, Optional<String> editRowKey) {
            this.rows = Objects.requireNonNull(rows);
            this.selectedRows = Objects.requireNonNull(selectedRows);
            this.editRowKey = editRowKey;
        }

        public Table(KeyedEntity<K, T>[] rows, Set<KeyedEntity<K, T>> selectedRows) {
            this.rows = Objects.requireNonNull(rows);
            this.selectedRows = Objects.requireNonNull(selectedRows);
            this.editRowKey = Optional.empty();
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

        public Table<K, T> withEditRowKey(Optional<String> rowKey) {
            return new Table<>(this.rows, this.selectedRows, rowKey);
        }
    }
}
