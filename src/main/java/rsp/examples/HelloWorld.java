package rsp.examples;

import rsp.App;
import rsp.jetty.JettyServer;

import static rsp.dsl.Html.*;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        final var app = new App<>("Hello world!",
                                  s -> html(
                                            body(
                                                 p(s.get())
                                                )
                                            )
                                  );
        final var server = new JettyServer(8080, "", app);
        server.start();
        server.join();
    }
}
