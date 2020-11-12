package rsp.examples.crud.state;


import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Objects;

public class Row<K,T> {
    public final K key;
    public final Class<T> clazz;
    public final Cell[] cells;

    public Row(K key, Class<T> clazz, Cell... cells) {
        this.key = Objects.requireNonNull(key);
        this.clazz = clazz;
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

    public T toEntity() {
        final Objenesis objenesis = new ObjenesisStd();
        final T obj = objenesis.newInstance(clazz);
        for (Cell cell: cells) {
            try {
                final Field declaredField = clazz.getDeclaredField(cell.fieldName);
                declaredField.setAccessible(true);
                declaredField.set(obj, cell.data);
                declaredField.setAccessible(false);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return obj;
    }
}