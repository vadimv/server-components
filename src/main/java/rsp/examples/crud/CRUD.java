package rsp.examples.crud;

import rsp.App;
import rsp.Component;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.SimpleAuthorsBooksService;
import rsp.examples.crud.components.Grid;
import rsp.jetty.JettyServer;

import java.util.HashSet;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class CRUD {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final SimpleAuthorsBooksService authorsBooksService = new SimpleAuthorsBooksService();
        AuthorsBooksServiceStubInit.init(authorsBooksService);
        final Component<State> render = s ->
                html(body(
                        of(authorsBooksService.books().thenApply(books ->
                                                new Grid.GridState(books.stream().map(b ->
                                                        new Grid.Row(
                                                                new Grid.Cell(b.id),
                                                                new Grid.Cell(b.title),
                                                                new Grid.Cell(b.authors.stream().map(a -> a.toString()).collect(Collectors.toList()))
                                                        )).toArray(Grid.Row[]::new),
                                                        0,
                                                        new HashSet<>())).thenApply(gridState ->
                                                                                Grid.component.of(useState(() -> gridState)))
                                                                         .exceptionally(t ->
                                                                                 div("Exception:" + t.getMessage())))

        ));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      new App<>(new State(), render));
        s.start();
        s.join();
    }

}
