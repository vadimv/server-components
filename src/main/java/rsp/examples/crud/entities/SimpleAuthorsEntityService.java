package rsp.examples.crud.entities;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SimpleAuthorsEntityService implements EntityService<Long, Author> {
    private long idGenerator;
    private final Map<Long, Author> authors = new HashMap<>();

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Author>>> getOne(Long key) {
        final Author a = authors.get(key);
        return CompletableFuture.completedFuture(a == null ? Optional.empty() : Optional.of(new KeyedEntity<>(key, a)));
    }

    @Override
    public CompletableFuture<List<KeyedEntity<Long, Author>>> getList(int offset, int limit) {
        return CompletableFuture.completedFuture(new ArrayList<>(authors.entrySet().stream().map(e ->
                new KeyedEntity<>(e.getKey(), e.getValue())).collect(Collectors.toList())));
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Author>>> create(Author entity) {
        long key = idGenerator++;
        authors.put(key, entity);
        return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key, entity)));
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Author>>> delete(Long key) {
        final Author a = authors.remove(key);
        if (a == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key,a)));
        }
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Author>>> update(KeyedEntity<Long, Author> updatedKeyedEntity) {
        final Author a = authors.get(updatedKeyedEntity.key);
        if (a == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            authors.put(updatedKeyedEntity.key, updatedKeyedEntity.data);
            return CompletableFuture.completedFuture(Optional.of(updatedKeyedEntity));
        }
    }
}
