package rsp.app;

import rsp.component.ComponentView;
import rsp.component.definitions.InitialStateComponent;
import rsp.jetty.WebServer;

import static rsp.dsl.Html.*;

public final class Counter {
    static void main(final String[] args) {

        final ComponentView<Integer> view = newState -> state ->
                html(
                    body(
                            h1("Current count: " + state),
                            button(on("click", _ -> newState.setState(state + 1)),
                            text("Increment"))
                    )
                );
        final var server = new WebServer(8080, _ -> new InitialStateComponent<>(0, view));
        System.out.println("http://localhost:8080");
        server.start();
        server.join();
    }
}