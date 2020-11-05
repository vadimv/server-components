package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static rsp.dsl.Html.*;

public class Grid implements Component<Grid.GridState> {


    @Override
    public DocumentPartDefinition of(UseState<GridState> state) {
        return div(
                table(
                        tbody(
                                Html.of(Arrays.stream(state.get().rows).map(row -> tr(
                                        td(input(attr("type", "checkbox"),
                                                when(state.get().selectedRows.contains(row), attr("checked")),
                                                attr("autocomplete", "off"),
                                                on("click", ctx -> state.accept(state.get().toggleRowSelection(row))))),
                                        Html.of(Arrays.stream(row.cells).map(field -> td(text(field))))
                                )))))
        );
    }

    public static class Cell<T> {
        private final T data;

        public Cell(T data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return data.toString();
        }
    }


    public static class Row<K> {
        public final K key;
        public final Cell[] cells;

        public Row(K key, Cell... cells) {
            this.key = Objects.requireNonNull(key);
            this.cells = cells;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Row row = (Row) o;
            return key.equals(row.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }

    public static class GridState<K> {
        public final Row[] rows;
        public final int keyRowIndex;
        public final Set<Row<K>> selectedRows;

        public GridState(Row[] rows, int keyRowIndex, Set<Row<K>> selectedRows) {
            this.rows = rows;
            this.keyRowIndex = keyRowIndex;
            this.selectedRows = selectedRows;
        }

        public GridState toggleRowSelection(Row<K> row) {
            final Set<Row> sr = new HashSet<>(selectedRows);
            if (selectedRows.contains(row)) {
                sr.remove(row);
            } else {
                sr.add(row);
            }
            return new GridState(rows, keyRowIndex, sr);
        }
    }
}
