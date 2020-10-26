package rsp.examples.components.entities;

public class Author {
    public final long id;
    public final Name name;
    public final Book[] books;

    public Author(long id, Name name, Book[] books) {
        this.id = id;
        this.name = name;
        this.books = books;
    }

    public Author(long id, Name name) {
        this(id, name, new Book[] {});
    }
}
