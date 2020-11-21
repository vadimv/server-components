package rsp.examples.crud.state;

import rsp.examples.crud.entities.KeyedEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Table<K, T> {
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
