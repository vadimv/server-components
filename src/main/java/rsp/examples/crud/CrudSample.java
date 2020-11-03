package rsp.examples.crud;

import rsp.App;
import rsp.Component;
import rsp.examples.crud.components.Admin;
import rsp.examples.crud.components.Grid;
import rsp.examples.crud.components.Resource;
import rsp.examples.crud.entities.Author;
import rsp.examples.crud.entities.AuthorsBooksServiceStubInit;
import rsp.examples.crud.entities.SimpleAuthorsEntityService;
import rsp.examples.crud.entities.SimpleBooksEntityService;
import rsp.jetty.JettyServer;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class CrudSample {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final SimpleBooksEntityService booksService = new SimpleBooksEntityService();
        final SimpleAuthorsEntityService authorsService = new SimpleAuthorsEntityService();
        AuthorsBooksServiceStubInit.init(authorsService, booksService);

        final Component<State> render = new Admin(new Resource("authors", new Grid()), new Resource("books", new Grid()));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      new App<State>(request -> {
                                          if (request.path.contains("books")) {
                                              return booksService.getList(0, 25).thenApply(books ->
                                                      new Grid.GridState(books.stream().map(b ->
                                                              new Grid.Row(
                                                                      new Grid.Cell(b.key),
                                                                      new Grid.Cell(b.entity.title),
                                                                      new Grid.Cell(b.entity.authors.stream().map(a -> a.entity.toString()).collect(Collectors.toList()))
                                                              )).toArray(Grid.Row[]::new),
                                                              0,
                                                              new HashSet<>())).
                                                      thenApply(gridState -> new State("books", "list", gridState));
                                              } else if (request.path.contains("authors")) {
                                                  return authorsService.getList(0, 25).thenApply(authors ->
                                                          new Grid.GridState(authors.stream().map(a ->
                                                                  new Grid.Row(
                                                                          new Grid.Cell(a.key),
                                                                          new Grid.Cell(a.entity.name),
                                                                          new Grid.Cell(a.entity.books.stream().map(b -> b.entity.toString()).collect(Collectors.toList()))
                                                                  )).toArray(Grid.Row[]::new),
                                                                  0,
                                                                  new HashSet<>())).thenApply(gridState -> new State("authors", "list", gridState));
                                              } else {
                                                return CompletableFuture.completedFuture(new State("unkown", "", null));
                                            }
                                          }, render));
        s.start();
        s.join();
    }

}
