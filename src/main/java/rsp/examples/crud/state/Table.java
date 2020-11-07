package rsp.examples.crud.state;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Table<K> {
    public final Row[] rows;
    public final Set<Row<K>> selectedRows;

    public Table(Row[] rows, Set<Row<K>> selectedRows) {
        this.rows = Objects.requireNonNull(rows);
        this.selectedRows = Objects.requireNonNull(selectedRows);
    }

    public static <K> Table<K> empty() {
        return new Table(new Row[] {}, Set.of());
    }

    public Table toggleRowSelection(Row<K> row) {
        final Set<Row> sr = new HashSet<>(selectedRows);
        if (selectedRows.contains(row)) {
            sr.remove(row);
        } else {
            sr.add(row);
        }
        return new Table(rows, sr);
    }
}
