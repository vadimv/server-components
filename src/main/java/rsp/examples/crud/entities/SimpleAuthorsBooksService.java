package rsp.examples.crud.entities;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SimpleAuthorsBooksService implements AuthorsBooksService {

    private long authorsIdGenerator;
    private long booksIdGenerator;
    private final Map<Long, Author> authors = new HashMap<>();
    private final Map<Long, Book> books = new HashMap<>();

    @Override
    public CompletableFuture<List<Author>> authors() {
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
        final var result = getAuthor(author.id).thenCombine(getBook(book.id), (ao, bo) -> {
            return ao.flatMap(a -> bo.flatMap(b -> {
                var newBookAuthors = new HashSet(b.authors);
                newBookAuthors.add(a);
                final Book nb = new Book(b.id, b.title, b.description, newBookAuthors);
                books.put(b.id, nb);

                var newAuthorsBooks = new HashSet(a.books);
                newAuthorsBooks.add(b);
                final Author na = new Author(a.id, a.name, newAuthorsBooks);
                authors.put(a.id, na);
                return Optional.of(na);
            }));
        });
        return result;
    }

    @Override
    public CompletableFuture<Optional<Author>> unAssign(Author author, Book book) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<Book>> getBook(long id) {
        final Optional<Book> b = Optional.ofNullable(books.get(id));
        return CompletableFuture.completedFuture(b);
    }

    @Override
    public CompletableFuture<List<Book>> books() {
        return CompletableFuture.completedFuture(new ArrayList<>(books.values()));
    }

    @Override
    public CompletableFuture<Book> createBook(String title, String desc, Author... authors) {
        final Book book = new Book(++booksIdGenerator, title, desc);
        books.put(book.id, book);
        for(Author author : authors) {
            assign(author, book);
        }
        return CompletableFuture.completedFuture(books.get(book.id));
    }
}
