package rsp.examples.crud.services;

import rsp.examples.crud.entities.KeyedEntity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface Delete<K, T> {
    CompletableFuture<Optional<KeyedEntity<K, T>>> delete(K key);
}
