package rsp.examples.crud.entities.services;

import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.Book;
import rsp.examples.crud.entities.KeyedEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SimpleDb {
    public AtomicLong authorsIdGenerator = new AtomicLong();
    public final Map<String, Author> authors = new HashMap<>();

    public AtomicLong booksIdGenerator =  new AtomicLong();
    public final Map<String, Book> books = new HashMap<>();

    public EntityService<String, Author> authorsService() {
        return new SimpleAuthorsEntityService();
    }

    public EntityService<String, Book> booksService() {
        return new SimpleBooksEntityService();
    }


    private class SimpleAuthorsEntityService implements EntityService<String, Author> {
        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Author>>> getOne(String key) {
            final Author a = authors.get(key);
            return CompletableFuture.completedFuture(a == null ? Optional.empty() : Optional.of(new KeyedEntity<>(key, a)));
        }

        @Override
        public CompletableFuture<List<KeyedEntity<String, Author>>> getList(int offset, int limit) {
            return CompletableFuture.completedFuture(new ArrayList<>(authors.entrySet().stream().map(e ->
                    new KeyedEntity<>(e.getKey(), e.getValue())).collect(Collectors.toList())));
        }

        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Author>>> create(Author entity) {
            final String key = Long.toString(authorsIdGenerator.incrementAndGet());
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
        public CompletableFuture<Optional<KeyedEntity<String, Author>>> delete(String key) {
            final Author a = authors.remove(key);
            if (a == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            } else {
                return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key,a)));
            }
        }

        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Author>>> update(KeyedEntity<String, Author> updatedKeyedEntity) {
            final Author a = authors.get(updatedKeyedEntity.key);
            if (a == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            } else {
                authors.put(updatedKeyedEntity.key, updatedKeyedEntity.data);
                return CompletableFuture.completedFuture(Optional.of(updatedKeyedEntity));
            }
        }
    }

    public class SimpleBooksEntityService implements EntityService<String, Book> {
        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Book>>> getOne(String key) {
            final Book a = books.get(key);
            return CompletableFuture.completedFuture(a == null ? Optional.empty() : Optional.of(new KeyedEntity<>(key, a)));
        }

        @Override
        public CompletableFuture<List<KeyedEntity<String, Book>>> getList(int offset, int limit) {
            return CompletableFuture.completedFuture(new ArrayList<>(books.entrySet().stream().map(e ->
                    new KeyedEntity<>(e.getKey(), e.getValue())).collect(Collectors.toList())));
        }

        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Book>>> create(Book entity) {
            String key = Long.toString(booksIdGenerator.incrementAndGet());
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
        public CompletableFuture<Optional<KeyedEntity<String, Book>>> delete(String key) {
            final Book a = books.remove(key);
            if (a == null) {
                return CompletableFuture.completedFuture(Optional.empty());
            } else {
                return CompletableFuture.completedFuture(Optional.of(new KeyedEntity<>(key,a)));
            }
        }

        @Override
        public CompletableFuture<Optional<KeyedEntity<String, Book>>> update(KeyedEntity<String, Book> updatedKeyedEntity) {
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
