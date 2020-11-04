package rsp.examples.crud;

import rsp.examples.crud.components.Admin;
import rsp.examples.crud.components.Grid;
import rsp.examples.crud.components.Resource;
import rsp.examples.crud.entities.*;
import rsp.jetty.JettyServer;

public class CrudSample {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final EntityService<Long, Author> authorsService = new SimpleAuthorsEntityService();
        final EntityService<Long, Book> booksService = new SimpleBooksEntityService();

        AuthorsBooksServiceStubInit.init(authorsService, booksService);

        final Admin admin = new Admin(new Resource<>("authors", authorsService, new Grid()),
                                      new Resource<>("books", booksService, new Grid()));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      admin.app());
        s.start();
        s.join();
    }

}
