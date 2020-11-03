package rsp.examples.crud.entities;

import java.util.Set;

public class Book {
    public final String title;
    public final String description;
    public final Set<KeyedEntity<Long, Author>> authors;

    public Book(String title, String description, Set<KeyedEntity<Long, Author>>  authors) {
        this.title = title;
        this.description = description;
        this.authors = authors;
    }

    public Book(long id, String title, String description) {
        this(title, description, Set.of());
    }
}
