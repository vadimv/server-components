package rsp.examples.crud;

import rsp.examples.crud.components.*;
import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.Book;
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

        final Admin admin = new Admin(new Resource<Author>("authors",
                                                     authorsService,
                                                     new DataGrid<Author>(new DataGrid.Header("Name", ""),
                                                                    e -> new RowFields(e.key,
                                                                                    new TextField<>(e.data.name),
                                                                                    new EditButton(e.key))),
                                                     null,
                                                                  null));
                                  //                   new Edit<>(d -> new Form<>(m -> d.accept(new Author(m.get("name")))), new TextInput<>("name", d.get().toString()))),
                                  //                   new Create<>(i -> new Form<>(m -> i.accept(map2obj(m)), new TextInput<>("name", "", )))));
                /*
                                      new Resource<Book>("books",
                                                     booksService,
                                                     new Grid<>(new TextField("title"),
                                                                new EditButton()),
                                                     new EditForm<>(new TextInput<>("title", s -> s)),
                                                     new EditForm<String, Book>(new InitialValue<>(new TextInput<>("title", s -> s), "")))));
*/
        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      admin.app());
        s.start();
        s.join();
    }

}
