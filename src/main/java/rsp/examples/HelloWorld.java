package rsp.examples;

import rsp.component.definitions.InitialStateComponentDefinition;
import rsp.jetty.WebServer;
import rsp.component.View;

import static rsp.html.HtmlDsl.*;

/**
 * Run the class and navigate to http://localhost:8080.
 */
public final class HelloWorld {
    public static void main(final String[] args) {
        final View<String> view = message -> html(
                                                body(
                                                      p(message)
                                                )
                                           );

        final var server = new WebServer(8080, __ -> new InitialStateComponentDefinition<>("Hello world", view));
        server.start();
        server.join();
    }
}
