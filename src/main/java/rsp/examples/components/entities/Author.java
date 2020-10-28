package rsp.examples.components.entities;

import java.util.Set;

public class Author {
    public final long id;
    public final Name name;
    public final Set<Book> books;

    public Author(long id, Name name, Set<Book> books) {
        this.id = id;
        this.name = name;
        this.books = books;
    }

    public Author(long id, Name name) {
        this(id, name, Set.of());
    }
}
