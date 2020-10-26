package rsp.examples.components.entities;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SimpleAuthorsBooksService implements AuthorsBooksService {

    private long authorsIdGenerator;
    private long booksIdGenerator;
    private final Map<Long, Author> authors = new HashMap<>();
    private final Map<Long, Book> books = new HashMap<>();

    @Override
    public CompletableFuture<List<Author>> students() {
        return CompletableFuture.completedFuture(new ArrayList<>(authors.values()));
    }

    @Override
    public CompletableFuture<Author> createAuthor(Name name) {
        final Author a = new Author(++authorsIdGenerator, name);
        authors.put(a.id, a);
        return CompletableFuture.completedFuture(a);
    }

    @Override
    public CompletableFuture<Optional<Author>> getAuthor(long id) {
        final Optional<Author> a = Optional.ofNullable(authors.get(id));
        return CompletableFuture.completedFuture(a);
    }

    @Override
    public CompletableFuture<Optional<Author>> assign(Author author, Book book) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<Author>> unAssign(Author author, Book book) {
        return null;
    }

    @Override
    public CompletableFuture<List<Book>> books() {
        return CompletableFuture.completedFuture(new ArrayList<>(books.values()));
    }

    @Override
    public CompletableFuture<Book> createBook(String name) {
        final Book b = new Book(++booksIdGenerator, name);
        books.put(b.id, b);
        return CompletableFuture.completedFuture(b);
    }
}
