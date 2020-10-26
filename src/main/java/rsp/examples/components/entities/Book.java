package rsp.examples.components.entities;

public class Book {
    public final long id;
    public final String name;
    public final Author[] authors;

    public Book(long id, String name, Author[] authors) {
        this.id = id;
        this.name = name;
        this.authors = authors;
    }

    public Book(long id, String name) {
        this(id, name, new Author[] {});
    }
}
