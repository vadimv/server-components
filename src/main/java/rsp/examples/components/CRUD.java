package rsp.examples.components;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class CRUD {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {

        final Component<State> render = state ->
                html(body(subComponent.of(useState(() -> state.get().i, s -> state.accept(new State(s)))),
                           div(span(text("+1")),
                               on("click",
                                  d -> { state.accept(new State(state.get().i + 1));})),
                           div(span(style("background-color", state.get().i % 2 ==0 ? "red" : "blue"), text(state.get().i)))
        ));

        final var s = new JettyServer(DEFAULT_PORT,
                              "",
                                      new App<>(new State(0), render));
        s.start();
        s.join();
    }

    public static final Component<Integer> subComponent = state ->
            div(text("+10"),
                on("click", d -> { state.accept(state.get() + 10);}));
}
