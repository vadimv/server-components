package rsp.examples.crud.entities;

import rsp.examples.crud.state.Row;
import rsp.util.Tuple2;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

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

    public Row<?,?> toRow() {
        final Stream<Field> fields = Arrays.stream(data.getClass().getFields()).filter(f ->
                Modifier.isPublic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()));
        final Tuple2[] keyedData = fields.map(f -> readField(f, data)).toArray(Tuple2[]::new);
        return new Row(key,
                       data.getClass(),
                       Arrays.stream(keyedData).map(t -> t._1).toArray(String[]::new),
                       Arrays.stream(keyedData).map(t -> t._2).toArray(Object[]::new));
    }

    private Tuple2 readField(Field f, T data) {
        try {
            return new Tuple2(f.getName(), f.get(data));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
