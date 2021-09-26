package rsp.browserautomation;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.routing.Routing;

import java.util.concurrent.CompletableFuture;

import static rsp.html.HtmlDsl.*;
import static rsp.routing.Routing.get;

public class SimpleServer {

    public static final int PORT = 8085;
    public final JettyServer jetty;

    public SimpleServer(JettyServer jetty) {
        this.jetty = jetty;
    }

    public static void main(String[] args) {
        run(true);
    }

    public static SimpleServer run(boolean blockCurrentThread) {
        final App<AppState> app = new App<>(routes(),
                                            appComponent());
        final SimpleServer s = new SimpleServer(new JettyServer(PORT, "", app));
        s.jetty.start();
        if (blockCurrentThread) {
            s.jetty.join();
        }
        return s;
    }

    private static Routing<AppState> routes() {
        return new Routing<>(new NotFoundState(),
                             get("/:id(^\\d+$)", (id, __) -> new OkState(Integer.parseInt(id)).toCompletableFuture()));
    }

    private static Component<? extends AppState> appComponent() {
        final Component<OkState> okComponent = state ->
                html(head(title("test-server-title")),
                        body(subComponent.render(state.get().i, s -> state.accept(new OkState(s))),
                                div(button(attr("type", "button"),
                                        attr("id", "b0"),
                                        text("+1"),
                                        on("click",
                                                d -> { state.accept(new OkState(state.get().i + 1));}))),
                                div(span(attr("id", "s0"),
                                        style("background-color", state.get().i % 2 ==0 ? "red" : "blue"),
                                        text(state.get().i)))
                        ));

        final Component<NotFoundState> notFoundComponent =
                state -> html(headPlain(title("Not found")),
                        body(h1("Not found 404"))).statusCode(404);

        final Component<? extends AppState> appComponent = s -> {
            if (s.isInstanceOf(NotFoundState.class)) {
                return notFoundComponent.render(s.cast(NotFoundState.class));
            } else if (s.isInstanceOf(OkState.class)) {
                return okComponent.render(s.cast(OkState.class));
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
        private final int i;

        public OkState(int i) {
            this.i = i;
        }

        public CompletableFuture<AppState> toCompletableFuture() {
            return CompletableFuture.completedFuture(this);
        }
    }

    public static final Component<Integer> subComponent = state ->
            div(attr("id", "d0"),
                text("+10"),
                on("click", d -> { state.accept(state.get() + 10);}));
}
