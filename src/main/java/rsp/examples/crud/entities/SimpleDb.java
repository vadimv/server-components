package rsp.examples.crud.entities;

import java.util.HashMap;
import java.util.Map;

public class SimpleDb {
    public long authorsIdGenerator;
    public final Map<Long, Author> authors = new HashMap<>();
    public long booksIdGenerator;
    public final Map<Long, Book> books = new HashMap<>();
}
