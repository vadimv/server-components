package rsp.examples.crud.entities;

import rsp.examples.crud.components.Grid;
import rsp.examples.crud.state.Cell;
import rsp.examples.crud.state.Row;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class KeyedEntity<K, T> {
    public final K key;
    public final T data;

    public KeyedEntity(K key, T data) {
        this.key = key;
        this.data = data;
    }

    public KeyedEntity<K, T> update(T updatedData) {
        return new KeyedEntity<>(key, updatedData);
    }

    public Row toRow() {
        final Field[] fields = data.getClass().getFields();
        final Cell[] cells =  Arrays.stream(fields).filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()))
                .map(f -> readField(f, data)).toArray(Cell[]::new);
        return new Row(key, data.getClass(), cells);
    }

    private Cell readField(Field f, T data) {
        try {
            return new Cell(f.getName(), f.get(data));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
