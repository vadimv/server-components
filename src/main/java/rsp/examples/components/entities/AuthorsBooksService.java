package rsp.examples.components.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface AuthorsBooksService {

    CompletableFuture<List<Author>> students();

    CompletableFuture<Author> createAuthor(Name name);

    CompletableFuture<Optional<Author>> getAuthor(long id);

    CompletableFuture<Optional<Author>> assign(Author author, Book book);

    CompletableFuture<Optional<Author>> unAssign(Author author, Book book);

    CompletableFuture<List<Book>> books();

    CompletableFuture<Book> createBook(String name);
}
