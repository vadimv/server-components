package rsp.browserautomation;

import rsp.App;
import rsp.html.SegmentDefinition;
import rsp.routing.Routing;
import rsp.stateview.ComponentView;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;
import rsp.stateview.View;

import java.util.concurrent.CompletableFuture;

import static rsp.component.ComponentDsl.component;
import static rsp.component.ComponentDsl.statelessComponent;
import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

public class SimpleServer {

    public static final int PORT = 8085;
    public static final int COUNTER_1_INITIAL_VALUE = 8000;
    public final JettyServer<AppState> jetty;

    static SegmentDefinition incrementCounterComponent(final String name, final int initialValue) {
        return component(initialValue, state -> newState ->
                div(div(button(attr("type", "button"),
                                attr("id", name + "_b0"),
                                text("+1"),
                                on("click",
                                        d -> newState.set(state + 1)))),
                        div(span(attr("id", name + "_s0"),
                                style("background-color", state % 2 ==0 ? "red" : "blue"),
                                text(state)))
                ));
    }

    static final ComponentView<CounterState> countersComponentView = state -> newState ->
            html(head(title("test-server-title")),
                    body(incrementCounterComponent("c1", COUNTER_1_INITIAL_VALUE),
                         incrementCounterComponent("c2", state.i)
                    ));

    static final View<NotFoundState> notFoundStatelessView = __ ->
            html(head(HeadType.PLAIN, title("Not found")),
                 body(h1("Not found 404"))).statusCode(404);

    static final ComponentView<AppState> appComponentView = state -> newState ->
        switch (state) {
            case NotFoundState nfs -> statelessComponent(nfs, notFoundStatelessView);
            case CounterState counterState -> component(counterState, countersComponentView);
        };

    public SimpleServer(final JettyServer<AppState> jetty) {
        this.jetty = jetty;
    }

    public static void main(final String[] args) {
        run(true);
    }

    public static SimpleServer run(final boolean blockCurrentThread) {
        final App<AppState> app = new App<>(routing(),
                                            appComponentView);
        final SimpleServer s = new SimpleServer(new JettyServer<>(8085, "", app));
        s.jetty.start();
        if (blockCurrentThread) {
            s.jetty.join();
        }
        return s;
    }

    private static Routing<HttpRequest, AppState> routing() {
        return new Routing<>(get("/:id(^\\d+$)", (__, id) -> CompletableFuture.completedFuture(new CounterState(Integer.parseInt(id)))),
                            new NotFoundState() );
    }


    sealed interface AppState {
    }

    static final class NotFoundState implements AppState {
    }

    static final class CounterState implements AppState {
        public final int i;

        public CounterState(final int i) {
            this.i = i;
        }

        public CompletableFuture<AppState> toCompletableFuture() {
            return CompletableFuture.completedFuture(this);
        }
    }
}
