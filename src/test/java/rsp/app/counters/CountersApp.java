package rsp.app.counters;

import rsp.component.ComponentView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;

/**
 * This is a test application for demonstrating this framework's main concepts and usage patterns.
 * This class illustrates an initial request routing and usage of multiple instances of a component {@link ComponentView}
 * some of which are have their state synchronized with a browser's address bar paths and a query parameter and
 * one demonstrates how component's state can be cached.
 * <p>
 * An example test URL: http://localhost:8085/16/-1?c4=27
 */
public final class CountersApp {
    public static final int PORT = 8085;

    public final WebServer webServer;

    public CountersApp(final WebServer webServer) {
        this.webServer = webServer;
    }

    public static CountersApp run(final boolean blockCurrentThread) {
        final CountersApp s = new CountersApp(new WebServer(PORT,
                                                            CountersAppComponent::new,
                                                            new StaticResources(new File("src/test/java/rsp/app/counters"),
                                                            "/res/*")));
        s.webServer.start();
        if (blockCurrentThread) {
            s.webServer.join();
        }
        return s;
    }

    static void main(final String[] args) {
        run(true);
    }

}
