package rsp.app.counters;

import rsp.component.*;
import rsp.component.definitions.*;
import rsp.html.SegmentDefinition;
import rsp.jetty.WebServer;
import rsp.page.EventContext;
import rsp.routing.Routing;
import rsp.server.Path;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

public final class CountersApp {

    private static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();

    public static final int PORT = 8085;
    public final WebServer webServer;

    private static SegmentDefinition counterComponent1(HttpRequest httpRequest) {
        return new PathStateComponentDefinition<>(httpRequest.relativeUrl(),
                                                  routing(path("/:c(^\\d+$)/*", c -> Integer.parseInt(c)),
                                                              -1),
                                                 (count, path) -> Path.of("/" + count + "/" + path.get(1)),
                                                 counterView("c1"));
    }

    private static SegmentDefinition counterComponent2(HttpRequest httpRequest) {
        return new PathStateComponentDefinition<>(httpRequest.relativeUrl(),
                                                  routing(path("/*/:c(^\\d+$)", c -> Integer.parseInt(c)),
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
                                   counterButtonClickHandler(state, newState)))),
                        div(span(attr("id", name + "_s0"),
                                 attr("class", state % 2 == 0 ? "red" : "blue"),
                                 text(state)))));
    }

    private static Consumer<EventContext> counterButtonClickHandler(Integer state, StateUpdate<Integer> newState) {
        return  ec -> newState.setState(state + 1);
    }

    private static SegmentDefinition storedCounterComponent(final String name) {
        return new StoredStateComponentDefinition<>(123, counterView(name), stateStore);
    }

    private static ComponentView<Boolean> storedCounterView() {
        return newState -> state ->
                div(
                        when(state, storedCounterComponent("c3")),
                        input(attr("type", "checkbox"),
                                when(state, attr("checked", "checked")),
                                attr("id","c3"),
                                attr("name", "c3"),
                                on("click", checkboxClickHandler(state, newState))),
                        label(attr("for", "c3"),
                                text("Show counter 3"))
                );
    }

    private static Consumer<EventContext> checkboxClickHandler(Boolean state, StateUpdate<Boolean> newState) {
        return  __ -> newState.setState(!state);
    }

    private static SegmentDefinition storedCounterComponent() {
        return new InitialStateComponentDefinition<>(true, storedCounterView());
    }

    private static View<CountersAppState> rootView(final HttpRequest httpRequest) {
        return __ ->
                html(head(title("test-server-title"),
                                link(attr("rel", "stylesheet"),
                                        attr("href", "/res/style.css"))),
                        body(counterComponent1(httpRequest),
                             counterComponent2(httpRequest),
                             br(),
                             storedCounterComponent()
                        ));
    }

    private static final View<NotFoundState> notFoundStatelessView = __ ->
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);


    private static View<AppState> appComponentView(final HttpRequest httpRequest) {
        return state -> {
            if (state instanceof CountersAppState countersState) {
                return new InitialStateComponentDefinition<>(countersState, rootView(httpRequest));
            } else if (state instanceof NotFoundState notFoundState) {
                return new InitialStateComponentDefinition<>(notFoundState, notFoundStatelessView);
            } else {
                throw new IllegalStateException();
            }
        };
    }

    private static StatefulComponentDefinition<AppState> rootComponent(final HttpRequest httpRequest) {
        final var appRouting = new Routing<>(get("/:c1(^\\d+$)/:c2(^\\d+$)", __ -> new CountersAppState()),
                                             new NotFoundState());
        return new HttpRequestStateComponentDefinition<>(httpRequest,
                                                         appRouting,
                                                         appComponentView(httpRequest));
    }

    public CountersApp(final WebServer webServer) {
        this.webServer = webServer;
    }

    public static CountersApp run(final boolean blockCurrentThread) {

        final CountersApp s = new CountersApp(new WebServer(8085,
                                                            CountersApp::rootComponent,
                                                            new StaticResources(new File("src/test/java/rsp/browserautomation"),
                                                           "/res/*")));
        s.webServer.start();
        if (blockCurrentThread) {
            s.webServer.join();
        }
        return s;
    }

    public static void main(final String[] args) {
        run(true);
    }

    sealed interface AppState {
    }

    static final class NotFoundState implements AppState {
    }

    static final class CountersAppState implements AppState {
    }
}
