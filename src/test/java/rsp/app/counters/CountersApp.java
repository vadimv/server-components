package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.StateUpdate;
import rsp.component.View;
import rsp.component.definitions.*;
import rsp.component.definitions.lookup.AddressBarLookupComponent;
import rsp.component.definitions.lookup.LookupStateComponent;
import rsp.dsl.Definition;
import rsp.jetty.WebServer;
import rsp.page.EventContext;
import rsp.routing.Routing;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static rsp.dsl.Html.*;
import static rsp.routing.RoutingDsl.*;

/**
 * http://localhost:8085/16/-1?c4=27
 */
public final class CountersApp {
    public static final int PORT = 8085;
    private static final Map<ComponentCompositeKey, Integer> stateStore = new HashMap<>();

    public final WebServer webServer;

    private static Definition counterComponent1() {
        return new LookupStateComponent<>("c1",
                                                      Integer::parseInt,
                                                      Object::toString,
                                                      counterView("c1"));
    }

    private static Definition counterComponent2() {
        return new LookupStateComponent<>("c2",
                                                      Integer::parseInt,
                                                      Object::toString,
                                                      counterView("c2"));
    }

    private static Definition counterComponent4() {
        return new LookupStateComponent<>("c4",
                                                   v -> v == null ? 0 : Integer.parseInt(v),
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

    private static Definition storedCounterComponent(final String name) {
        return new StoredStateComponent<>(123, counterView(name), stateStore);
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

    private static Definition storedCounterComponent() {
        return new InitialStateComponent<>(true, storedCounterView());
    }

    private static View<CountersAppState> rootView(HttpRequest httpRequest, CountersAppState countersState) {
        return _ ->
                html(head(title("Counters"),
                                link(attr("rel", "stylesheet"),
                                        attr("href", "/res/style.css"))),
                        body(
                                AddressBarLookupComponent.of(httpRequest.relativeUrl(),
                                                new InitialStateComponent<>(countersState, mainView(countersState)))
                                        .withPathElement(0, "c1")
                                        .withPathElement(1, "c2")
                                        .withQueryParameter("c4", "c4")

                        ));
    }

    private static View<CountersAppState> mainView(CountersAppState countersState) {
        return  _ -> div(counterComponent1(),
                                br(),
                                counterComponent2(),
                                br(),
                                storedCounterComponent(),
                                br(),
                                counterComponent4()
                        );
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

    private static StatefulComponent<AppState> rootComponent(final HttpRequest httpRequest) {
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
