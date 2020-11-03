package rsp.examples.crud.entities;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SimpleBooksEntityService implements EntityService<Long, Book> {
    private long idGenerator;
    private final Map<Long, Book> books = new HashMap<>();

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Book>>> getOne(Long key) {
        final Book a = books.get(key);
        return CompletableFuture.completedFuture(a == null ? Optional.empty() : Optional.of(new KeyedEntity<>(key, a)));
    }

    @Override
    public CompletableFuture<List<KeyedEntity<Long, Book>>> getList(int offset, int limit) {
        return CompletableFuture.completedFuture(new ArrayList<>(books.entrySet().stream().map(e ->
                new KeyedEntity<>(e.getKey(), e.getValue())).collect(Collectors.toList())));
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Book>>> create(Book entity) {
        long key = idGenerator++;
        books.put(key, entity);
        return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key, entity)));
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Book>>> delete(Long key) {
        final Book a = books.remove(key);
        if (a == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key,a)));
        }
    }

    @Override
    public CompletableFuture<Optional<KeyedEntity<Long, Book>>> update(KeyedEntity<Long, Book> updatedKeyedEntity) {
        final Book a = books.get(updatedKeyedEntity.key);
        if (a == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        } else {
            books.put(updatedKeyedEntity.key, updatedKeyedEntity.entity);
            return CompletableFuture.completedFuture(Optional.of(updatedKeyedEntity));
        }
    }
}
