package rsp.examples.crud;

import rsp.examples.crud.components.*;
import rsp.examples.crud.entities.*;
import rsp.examples.crud.entities.services.EntityService;
import rsp.examples.crud.entities.services.SimpleDb;
import rsp.jetty.JettyServer;

public class CrudSample {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final SimpleDb db = new SimpleDb();
        final EntityService<String, Author> authorsService = db.authorsService();
        final EntityService<String, Book> booksService = db.booksService();

        AuthorsBooksServiceStubInit.init(authorsService, booksService);

        final Admin admin = new Admin(new Resource<>("authors",
                                                      authorsService,
                                                      new Grid<>(new TextField("name"),
                                                                 new TextField("books"),
                                                                 new EditButton()),
                                                      new EditForm<>(new TextInput("name"))),
                                      new Resource<>("books",
                                                     booksService,
                                                     new Grid<>(new TextField("title"),
                                                                new EditButton()),
                                                     new EditForm<>(new TextInput("title"))));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      admin.app());
        s.start();
        s.join();
    }

}
