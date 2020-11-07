package rsp.examples.crud.entities.services;

import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.Book;
import rsp.examples.crud.entities.KeyedEntity;
import rsp.examples.crud.entities.services.EntityService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SimpleDb {
    public AtomicLong authorsIdGenerator = new AtomicLong();
    public final Map<Long, Author> authors = new HashMap<>();

    public AtomicLong booksIdGenerator =  new AtomicLong();
    public final Map<Long, Book> books = new HashMap<>();

    public EntityService<Long, Author> authorsService() {
        return new SimpleAuthorsEntityService();
    }

    public EntityService<Long, Book> booksService() {
        return new SimpleBooksEntityService();
    }


    private class SimpleAuthorsEntityService implements EntityService<Long, Author> {
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
            long key = authorsIdGenerator.incrementAndGet();
            var ke = new KeyedEntity<>(key, entity);
            for (var book : entity.books) {
                booksService().getOne(book.key).thenAccept(bo -> {
                    bo.ifPresent(bke -> { bke.update(bke.data.addAuthor(ke));});
                });
            }

            authors.put(key, entity);
            return CompletableFuture.completedFuture(Optional.of(ke));
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

    public class SimpleBooksEntityService implements EntityService<Long, Book> {
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
            long key = booksIdGenerator.incrementAndGet();
            var ke = new KeyedEntity<>(key, entity);
            for (var author : entity.authors) {
                authorsService().getOne(author.key).thenAccept(bo -> {
                    bo.ifPresent(bke -> { authorsService().update(bke.update(bke.data.addBook(ke)));});
                });
            }

            books.put(key, entity);
            return CompletableFuture.completedFuture(Optional.of(ke));
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
                books.put(updatedKeyedEntity.key, updatedKeyedEntity.data);
                return CompletableFuture.completedFuture(Optional.of(updatedKeyedEntity));
            }
        }
    }
}
