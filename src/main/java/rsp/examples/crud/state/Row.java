package rsp.examples.crud.state;

import rsp.examples.crud.components.Grid;

import java.util.Objects;

public class Row<K> {
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