package rsp.examples.components;

import rsp.App;
import rsp.Component;
import rsp.examples.components.entities.AuthorsBooksServiceStubInit;
import rsp.examples.components.entities.SimpleAuthorsBooksService;
import rsp.examples.components.grid.GridComponent;
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
                                                new GridComponent.GridState(books.stream().map(b ->
                                                        new GridComponent.Row(
                                                                new GridComponent.Cell(b.id),
                                                                new GridComponent.Cell(b.title),
                                                                new GridComponent.Cell(b.authors.stream().map(a -> a.toString()).collect(Collectors.toList()))
                                                        )).toArray(GridComponent.Row[]::new),
                                                        0,
                                                        new HashSet<>())).thenApply(gridState ->
                                                            GridComponent.component.of(useState(() -> gridState)))
                                                                         .exceptionally(t -> div(text("Exception:" + t.getMessage()))))

        ));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      new App<>(new State(), render));
        s.start();
        s.join();
    }

}
