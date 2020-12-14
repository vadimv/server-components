package rsp.examples.crud;

import rsp.examples.crud.components.*;
import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.Book;
import rsp.examples.crud.services.EntityService;
import rsp.examples.crud.services.SimpleDb;
import rsp.jetty.JettyServer;


public class CrudSample {

    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        final SimpleDb db = new SimpleDb();
        final EntityService<String, Author> authorsService = db.authorsService();
        final EntityService<String, Book> booksService = db.booksService();

        AuthorsBooksServiceStubInit.init(authorsService, booksService);

        final Admin admin = new Admin("Authors and Books",
                                        new Resource<>("authors",
                                                        "Authors",
                                                        authorsService,
                                                        new DataGrid<>(new Column<>("Name", e -> new TextField<>(e.name)),
                                                                       new Column<>(e -> new EditButton())),
                                                        new Edit<>(d -> new Form(m -> m.apply("name").ifPresent(v -> d.accept(Author.of(v))),
                                                                new TextInput("name",
                                                                                "Name",
                                                                                d.get().toString(),
                                                                                new RequiredValidation()))),
                                                        new Create<>(d -> new Form(m -> m.apply("name").ifPresent(v -> d.accept(Author.of(v))),
                                                                                     new TextInput("name",
                                                                                            "Name",
                                                                                            "",
                                                                                                 new RequiredValidation())))));

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
