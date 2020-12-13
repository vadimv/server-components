package rsp.examples.crud.services;

import rsp.examples.crud.entities.KeyedEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetList<K, T> {
    CompletableFuture<List<KeyedEntity<K, T>>> getList(int offset, int limit);
}
