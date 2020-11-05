package rsp.examples.crud.entities;

import rsp.examples.crud.components.Grid;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

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

    public Grid.Row toRow() {
        final Field[] fields = data.getClass().getFields();
        final Grid.Cell[] cells =  Arrays.stream(fields).filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()))
                .map(f -> readField(f, data)).toArray(Grid.Cell[]::new);
        return new Grid.Row(key, cells);
    }

    private Grid.Cell readField(Field f, T data) {
        try {
            return new Grid.Cell(f.get(data));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
