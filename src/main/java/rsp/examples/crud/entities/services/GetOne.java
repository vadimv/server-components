package rsp.examples.crud.entities.services;

import rsp.examples.crud.entities.KeyedEntity;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface GetOne<K, T> {
    CompletableFuture<Optional<KeyedEntity<K, T>>> getOne(K key);
}
