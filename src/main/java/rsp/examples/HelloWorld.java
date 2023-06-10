package rsp.examples;

import rsp.App;
import rsp.jetty.JettyServer;
import rsp.stateview.ComponentView;

import static rsp.html.HtmlDsl.*;

/**
 * Run the class and navigate to http://localhost:8080.
 */
public class HelloWorld {
    public static void main(final String[] args) {
        final ComponentView<String> view = s -> sc -> html(
                                                                    body(
                                                                            p(s)
                                                                    )
                                                            );

        final var app = new App<>("Hello world!", view);
        final var server = new JettyServer<>(8080, "", app);
        server.start();
        server.join();
    }
}
