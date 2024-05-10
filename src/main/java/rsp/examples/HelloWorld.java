package rsp.examples;

import rsp.App;
import rsp.jetty.WebServer;
import rsp.component.View;

import static rsp.html.HtmlDsl.*;

/**
 * Run the class and navigate to http://localhost:8080.
 */
public final class HelloWorld {
    public static void main(final String[] args) {
        final View<String> view = state -> html(
                                                body(
                                                      p(state)
                                                )
                                           );

        final var app = new App<>("Hello world!", view);
        final var server = new WebServer(8080, app);
        server.start();
        server.join();
    }
}
