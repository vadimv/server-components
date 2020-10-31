package rsp.examples.crud.entities;

import java.util.Set;

public class Book {
    public final long id;
    public final String title;
    public final String description;
    public final Set<Author> authors;

    public Book(long id, String title, String description, Set<Author> authors) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.authors = authors;
    }

    public Book(long id, String title, String description) {
        this(id, title, description, Set.of());
    }
}
