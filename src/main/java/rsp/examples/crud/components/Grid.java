package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.state.UseState;

import java.util.Arrays;
import java.util.HashSet;
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
                                                when(state.get().selectedRows.contains(row), attr("checked", "checked")),
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


    public static class Row {
        public final Cell[] cells;

        public Row(Cell... cells) {
            this.cells = cells;
        }
    }

    public static class GridState {
        public final Row[] rows;
        public final int keyRowIndex;
        public final Set<Row> selectedRows;

        public GridState(Row[] rows, int keyRowIndex, Set<Row> selectedRows) {
            this.rows = rows;
            this.keyRowIndex = keyRowIndex;
            this.selectedRows = selectedRows;
        }

        public GridState toggleRowSelection(Row row) {
            final Set<Row> sr = new HashSet<>(selectedRows);
            sr.remove(row);
            return new GridState(rows, keyRowIndex, sr);
        }
    }
}
