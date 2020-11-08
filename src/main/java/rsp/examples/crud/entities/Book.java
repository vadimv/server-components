package rsp.examples.crud.entities;

import java.util.HashSet;
import java.util.Set;

public class Book {
    public final String title;
    public final String description;
    public final Set<KeyedEntity<String, Author>> authors;

    public Book(String title, String description, Set<KeyedEntity<String, Author>>  authors) {
        this.title = title;
        this.description = description;
        this.authors = authors;
    }

    public Book addAuthor(KeyedEntity<String, Author> author) {
        final var a = new HashSet<>(authors);
        a.add(author);
        return new Book(title, description, a);
    }
}
