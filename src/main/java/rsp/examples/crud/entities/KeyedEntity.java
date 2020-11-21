package rsp.examples.crud.entities;

import afu.org.checkerframework.checker.oigj.qual.O;
import rsp.util.Tuple2;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;

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

    public String[] dataFieldsNames() {
        return Arrays.stream(data.getClass().getFields()).filter(f ->
                Modifier.isPublic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())).map(f -> f.getName()).toArray(String[]::new);
    }

    public Optional<?> field(String fieldName) {
        try {
            return Optional.of(data.getClass().getField(fieldName).get(data));
        } catch (NoSuchFieldException ex) {
            return Optional.empty();
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Tuple2 readField(Field f, T data) {
        try {
            return new Tuple2(f.getName(), f.get(data));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
