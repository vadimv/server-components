package rsp.examples.crud.entities;

import java.util.HashSet;
import java.util.Set;

public class Author {
    public final Name name;
    public final Set<KeyedEntity<String, Book>> books;

    public Author(Name name, Set<KeyedEntity<String, Book>> books) {
        this.name = name;
        this.books = books;
    }

    public  Author(Name name) {
        this(name, Set.of());
    }

    public static Author of(String name) {
        return new Author(Name.of(name));
    }
    @Override
    public String toString() {
        return name.toString();
    }

    public Author addBook(KeyedEntity<String, Book> book) {
        final var b = new HashSet<>(books);
        b.add(book);
        return new Author(name, b);
    }

}
