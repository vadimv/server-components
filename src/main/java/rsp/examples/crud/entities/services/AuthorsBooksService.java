package rsp.examples.crud.entities.services;

import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.Book;
import rsp.examples.crud.entities.Name;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

interface AuthorsBooksService {

    CompletableFuture<List<Author>> authors();

    CompletableFuture<Author> createAuthor(Name name);

    CompletableFuture<Optional<Author>> getAuthor(long id);

    CompletableFuture<Optional<Author>> assign(Author author, Book book);

    CompletableFuture<Optional<Author>> unAssign(Author author, Book book);

    CompletableFuture<Optional<Book>> getBook(long id);

    CompletableFuture<List<Book>> books();

    CompletableFuture<Book> createBook(String title, String desc, Author... authors);
}
