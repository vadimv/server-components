package rsp.examples.crud.entities;

public class KeyedEntity<K, T> {
    public final K key;
    public final T entity;

    public KeyedEntity(K key, T entity) {
        this.key = key;
        this.entity = entity;
    }

    public KeyedEntity<K, T> update(T updatedEntity) {
        return new KeyedEntity<>(key, updatedEntity);
    }
}
