package rsp.examples;

import rsp.App;
import rsp.jetty.JettyServer;

import static rsp.html.HtmlDsl.*;

/**
 * Run the class and navigate to http://localhost:8080.
 */
public class HelloWorld {
    public static void main(String[] args) {
        final var app = new App<>("Hello world!",
                                  s -> html(
                                            body(
                                                 p(s.get())
                                                )
                                            )
                                  );
        final var server = new JettyServer<>(8080, "", app);
        server.start();
        server.join();
    }
}
