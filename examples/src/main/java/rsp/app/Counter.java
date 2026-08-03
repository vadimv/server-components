package rsp.app;

import rsp.component.ComponentView;
import rsp.component.definitions.LocalStateComponent;
import rsp.jetty.WebServer;

import static rsp.dsl.Html.*;

public final class Counter {
    private enum CounterIntent {
        INCREMENT
    }

    static void main(final String[] args) {

        final ComponentView<Integer, CounterIntent> view = intents -> state ->
                html(
                    body(
                            h1("Current count: " + state),
                            button(on("click", _ -> intents.dispatch(CounterIntent.INCREMENT)),
                            text("Increment"))
                    )
                );
        final var server = new WebServer(8080, _ ->
                new LocalStateComponent<>((_, _) -> 0, view, (state, intent) -> state + 1));
        System.out.println("http://localhost:8080");
        server.start();
        server.join();
    }
}
