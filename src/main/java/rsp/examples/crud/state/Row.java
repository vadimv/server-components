package rsp.examples.crud.state;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Objects;

public class Row<K,T> {
    public final K rowKey;
    public final Class<T> clazz;
    public final String[] dataKeys;
    public final Object[] data;

    public Row(K rowKey, Class<T> clazz, String[] dataKeys, Object[] data) {
        this.rowKey = Objects.requireNonNull(rowKey);
        this.clazz = Objects.requireNonNull(clazz);
        this.dataKeys = Objects.requireNonNull(dataKeys);
        this.data = Objects.requireNonNull(data);
        if (this.dataKeys.length != this.data.length) throw new IllegalStateException("Keys length not equals to data length");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Row row = (Row) o;
        return rowKey.equals(row.rowKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rowKey);
    }

    public T toEntity() {
        final Objenesis objenesis = new ObjenesisStd();
        final T obj = objenesis.newInstance(clazz);
        for (int i = 0; i < data.length; i++) {
            try {
                final Field declaredField = clazz.getDeclaredField(dataKeys[i]);
                declaredField.setAccessible(true);
                declaredField.set(obj, data[i]);
                declaredField.setAccessible(false);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return obj;
    }

}