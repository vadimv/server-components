package rsp.app.counters;

import rsp.component.View;
import rsp.component.definitions.*;
import rsp.jetty.WebServer;
import rsp.routing.Routing;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.io.File;

import static rsp.dsl.Html.*;
import static rsp.routing.RoutingDsl.*;

/**
 * This is a test application presenting usage of multiple instances of a component
 * some of which are have their state synchronized with a browser's address bar paths and a query parameter and
 * one demonstrates how component's state can be cached.
 * http://localhost:8085/16/-1?c4=27
 */
public final class CountersApp {
    public static final int PORT = 8085;

    public final WebServer webServer;

    private static View<CountersAppState> rootView(HttpRequest httpRequest, CountersAppState countersState) {
        return _ ->
                html(head(title("Counters"),
                                link(attr("rel", "stylesheet"),
                                        attr("href", "/res/style.css"))),
                     body(new CountersMainComponent(httpRequest.relativeUrl())));
    }


    private static final View<NotFoundState> notFoundStatelessView = _ ->
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);


    private static View<AppState> appComponentView(final HttpRequest httpRequest) {
        return state -> {
            if (state instanceof CountersAppState countersState) {
                return rootView(httpRequest, countersState).apply(countersState);
            } else if (state instanceof NotFoundState notFoundState) {
                return new InitialStateComponent<>(notFoundState, notFoundStatelessView);
            } else {
                throw new IllegalStateException();
            }
        };
    }

    private static Component<AppState> rootComponent(final HttpRequest httpRequest) {
        final var appRouting = new Routing<>(get("/:c1(^-?\\d+$)/:c2(^-?\\d+$)", _ -> new CountersAppState()),
                                             new NotFoundState());
        return new HttpRequestStateComponent<>(httpRequest,
                                              appRouting,
                                              appComponentView(httpRequest));
    }

    public CountersApp(final WebServer webServer) {
        this.webServer = webServer;
    }

    public static CountersApp run(final boolean blockCurrentThread) {

        final CountersApp s = new CountersApp(new WebServer(PORT,
                                                            CountersApp::rootComponent,
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

    sealed interface AppState {
    }

    static final class NotFoundState implements AppState {
    }

    static final class CountersAppState implements AppState {
    }
}
