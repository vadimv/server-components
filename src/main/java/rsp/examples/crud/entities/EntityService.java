package rsp.examples.crud.entities;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface EntityService<K, T> {

    CompletableFuture<Optional<KeyedEntity<K, T>>> getOne(K key);

    CompletableFuture<List<KeyedEntity<K, T>>> getList(int offset, int limit);

    CompletableFuture<Optional<KeyedEntity<K, T>>> create(T entity);

    CompletableFuture<Optional<KeyedEntity<K, T>>> delete(K key);

    CompletableFuture<Optional<KeyedEntity<K, T>>> update(KeyedEntity<K, T> updatedKeyedEntity);
}
