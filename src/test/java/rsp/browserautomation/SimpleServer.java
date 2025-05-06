package rsp.browserautomation;

import rsp.component.*;
import rsp.html.SegmentDefinition;
import rsp.jetty.WebServer;
import rsp.routing.Routing;
import rsp.server.Path;
import rsp.server.StaticResources;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

public class SimpleServer {

    private static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();

    public static final int PORT = 8085;
    public final WebServer jetty;

    private static SegmentDefinition counter1() {
        return new PathStateComponentDefinition<>(routing(path("/:c(^\\d+$)/*", c -> CompletableFuture.completedFuture(Integer.parseInt(c))),
                                                              -1),
                                                 (count, path) -> Path.of("/" + count + "/" + path.get(1)),
                                                 counterView("c1"));
    }

    private static SegmentDefinition counter2() {
        return new PathStateComponentDefinition<>(routing(path("/*/:c(^\\d+$)", c -> CompletableFuture.completedFuture(Integer.parseInt(c))),
                                                                -1),
                                                        (count, path) -> Path.of("/" + path.get(0) + "/" + count),
                                                        counterView("c2"));
    }

    private static ComponentView<Integer> counterView(final String name) {
        return newState -> state ->of(
                span(name),
                div(div(button(attr("type", "button"),
                                attr("id", name + "_b0"),
                                text("+1"),
                                on("click",
                                        d -> newState.setState(state + 1)))),
                        div(span(attr("id", name + "_s0"),
                                 attr("class", state % 2 == 0 ? "red" : "blue"),
                                 text(state)))));
    }

    private static SegmentDefinition storedCounter(final String name) {
        return new StoredStateComponentDefinition<>(123, counterView(name), stateStore);
    }

    private static ComponentView<Boolean> storedCounterView() {
        return newState -> state ->
                div(
                        when(state, storedCounter("c3")),
                        input(attr("type", "checkbox"),
                                when(state, attr("checked", "checked")),
                                attr("id","c3"),
                                attr("name", "c3"),
                                on("click", ctx -> {
                                    newState.setState(!state);
                                })),
                        label(attr("for", "c3"),
                                text("Show counter 3"))
                );
    }
    private static final View<CountersState> countersComponentView = state ->
            html(head(title("test-server-title"),
                            link(attr("rel", "stylesheet"),
                                 attr("href", "/res/style.css"))),
                    body(counter1(),
                         counter2(),
                         br(),
                         new InitialStateComponentDefinition<>( true, storedCounterView())
                    ));

    private static final View<NotFoundState> notFoundStatelessView = __ ->
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);


    private static StatefulComponentDefinition<AppState> rootComponent() {
        final var appRouting = new Routing<>(get("/:c1(^\\d+$)/:c2(^\\d+$)", __ -> CompletableFuture.completedFuture(new CountersState())),
                                             new NotFoundState());
        final View<AppState> appComponentView = state -> {
            if (state instanceof NotFoundState notFoundState) {
                return notFoundStatelessView.apply(notFoundState);
            } else if (state instanceof CountersState countersState) {
                return countersComponentView.apply(countersState);
            } else {
                throw new IllegalStateException();
            }
        };
        return new HttpRequestStateComponentDefinition<>(appRouting,
                                                         appComponentView);
    }

    public SimpleServer(final WebServer jetty) {
        this.jetty = jetty;
    }

    public static void main(final String[] args) {
        run(true);
    }

    public static SimpleServer run(final boolean blockCurrentThread) {

        final SimpleServer s = new SimpleServer(new WebServer(8085,
                                                               rootComponent(),
                                                              new StaticResources(new File("src/test/java/rsp/browserautomation"),
                                                                   "/res/*")));
        s.jetty.start();
        if (blockCurrentThread) {
            s.jetty.join();
        }
        return s;
    }


    sealed interface AppState {
    }

    static final class NotFoundState implements AppState {
    }

    static final class CountersState implements AppState {
    }
}
