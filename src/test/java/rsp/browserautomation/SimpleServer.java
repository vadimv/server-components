package rsp.browserautomation;

import rsp.App;
import rsp.UseStateComponentFunction;
import rsp.jetty.JettyServer;
import rsp.routing.Route;
import rsp.server.HttpRequest;

import java.util.concurrent.CompletableFuture;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

public class SimpleServer {

    public static final int PORT = 8085;
    public final JettyServer<AppState> jetty;

    public SimpleServer(JettyServer<AppState> jetty) {
        this.jetty = jetty;
    }

    public static void main(String[] args) {
        run(true);
    }

    public static SimpleServer run(boolean blockCurrentThread) {
        final App<AppState> app = new App<>(routes(),
                                            appComponent());
        final SimpleServer s = new SimpleServer(new JettyServer<>(PORT, "", app));
        s.jetty.start();
        if (blockCurrentThread) {
            s.jetty.join();
        }
        return s;
    }

    private static Route<HttpRequest, AppState> routes() {
        return concat(get("/:id(^\\d+$)", (__, id) -> new OkState(Integer.parseInt(id)).toCompletableFuture()),
                      any(new NotFoundState()));
    }

    private static UseStateComponentFunction<AppState> appComponent() {
        final UseStateComponentFunction<OkState> okUseStateComponentFunction = state ->
                html(head(title("test-server-title")),
                        body(SUB_STATE_VIEW.apply(state.get().i, s -> state.accept(new OkState(s))),
                                div(button(attr("type", "button"),
                                        attr("id", "b0"),
                                        text("+1"),
                                        on("click",
                                                d -> { state.accept(new OkState(state.get().i + 1));}))),
                                div(span(attr("id", "s0"),
                                        style("background-color", state.get().i % 2 ==0 ? "red" : "blue"),
                                        text(state.get().i)))
                        ));

        final UseStateComponentFunction<NotFoundState> notFoundComponent =
                state -> html(headPlain(title("Not found")),
                        body(h1("Not found 404"))).statusCode(404);

        final UseStateComponentFunction<AppState> appComponent = s -> {
            if (s.get() instanceof NotFoundState) {
                return notFoundComponent.apply((NotFoundState)s.get());
            } else if (s.get() instanceof OkState) {
                return okUseStateComponentFunction.apply((OkState)s.get());
            } else {
                // should never happen
                throw new IllegalStateException("Illegal state");
            }
        };
        return appComponent;
    }

    interface AppState {
    }

    public static class NotFoundState implements AppState {
    }

    private static class OkState implements AppState {
        public final int i;

        public OkState(int i) {
            this.i = i;
        }

        public CompletableFuture<AppState> toCompletableFuture() {
            return CompletableFuture.completedFuture(this);
        }
    }

    public static final UseStateComponentFunction<Integer> SUB_STATE_VIEW = state ->
            div(attr("id", "d0"),
                text("+10"),
                on("click", d -> { state.accept(state.get() + 10);}));
}
