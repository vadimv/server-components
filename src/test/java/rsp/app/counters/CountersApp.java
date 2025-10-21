package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.View;
import rsp.component.definitions.*;
import rsp.html.SegmentDefinition;
import rsp.jetty.WebServer;
import rsp.page.EventContext;
import rsp.routing.Routing;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

/**
 * http://localhost:8085/16/-1?c4=27
 */
public final class CountersApp {
    public static final int PORT = 8085;
    private static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();

    public final WebServer webServer;

    private static SegmentDefinition counterComponent1() {
        return new LookupStateComponentDefinition<>("c1",
                                                      Integer::parseInt,
                                                      Object::toString,
                                                      counterView("c1"));
    }

    private static SegmentDefinition counterComponent2() {
        return new LookupStateComponentDefinition<>("c2",
                                                      Integer::parseInt,
                                                      Object::toString,
                                                      counterView("c2"));
    }

    private static SegmentDefinition counterComponent4() {
        return new LookupStateComponentDefinition<>("c4",
                Integer::parseInt,
                Object::toString,
                counterView("c4"));
    }

    private static ComponentView<Integer> counterView(final String name) {
        return newState -> state ->
                         div(span(name),
                              button(attr("type", "button"),
                                     attr("id", name + "_b0"),
                                     text("+"),
                                     on("click",
                                         counterButtonClickHandlerPlus(state, newState))),
                              span(attr("id", name + "_s0"),
                                   attr("class", state % 2 == 0 ? "red" : "blue"),
                                   text(state)),
                              button(attr("type", "button"),
                                     attr("id", name + "_b1"),
                                     text("-"),
                                     on("click",
                                         counterButtonClickHandlerMinus(state, newState))));
    }

    private static Consumer<EventContext> counterButtonClickHandlerPlus(Integer state, StateUpdate<Integer> newState) {
        return  _ -> newState.setState(state + 1);
    }

    private static Consumer<EventContext> counterButtonClickHandlerMinus(Integer state, StateUpdate<Integer> newState) {
        return  _ -> newState.setState(state - 1);
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
        return  _ -> newState.setState(!state);
    }

    private static SegmentDefinition storedCounterComponent() {
        return new InitialStateComponentDefinition<>(true, storedCounterView());
    }

    private static View<CountersAppState> rootView() {
        return _ ->
                html(head(title("test-server-title"),
                                link(attr("rel", "stylesheet"),
                                        attr("href", "/res/style.css"))),
                        body(counterComponent1(),
                             br(),
                             counterComponent2(),
                             br(),
                             storedCounterComponent(),
                             counterComponent4()
                        ));
    }

    private static final View<NotFoundState> notFoundStatelessView = _ ->
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);


    private static View<AppState> appComponentView(final HttpRequest httpRequest) {
        return state -> {
            if (state instanceof CountersAppState countersState) {
                return AddressBarLookupComponentDefinition.of(httpRequest.relativeUrl(),
                                                                         new InitialStateComponentDefinition<>(countersState, rootView()))
                                                                     .withPathElement("c1")
                                                                     .withPathElement("c2")
                                                                     .withQueryParameter("c4", "c4");
            } else if (state instanceof NotFoundState notFoundState) {
                return new InitialStateComponentDefinition<>(notFoundState, notFoundStatelessView);
            } else {
                throw new IllegalStateException();
            }
        };
    }

    private static StatefulComponentDefinition<AppState> rootComponent(final HttpRequest httpRequest) {
        final var appRouting = new Routing<>(get("/:c1(^-?\\d+$)/:c2(^-?\\d+$)", _ -> new CountersAppState()),
                                             new NotFoundState());
        return new HttpRequestStateComponentDefinition<>(httpRequest,
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
