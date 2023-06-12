package rsp.browserautomation;

import rsp.App;
import rsp.stateview.ComponentView;
import rsp.jetty.JettyServer;
import rsp.routing.Route;
import rsp.server.HttpRequest;
import rsp.stateview.NewState;
import rsp.stateview.View;

import java.util.concurrent.CompletableFuture;

import static rsp.component.ComponentDsl.component;
import static rsp.html.HtmlDsl.*;
import static rsp.routing.RoutingDsl.*;

public class SimpleServer {

    public static final int PORT = 8085;
    public final JettyServer<AppState> jetty;

    public SimpleServer(final JettyServer<AppState> jetty) {
        this.jetty = jetty;
    }

    public static void main(final String[] args) {
        run(true);
    }

    public static SimpleServer run(final boolean blockCurrentThread) {
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


    private static ComponentView<AppState> appComponent() {
        final ComponentView<OkState> okComponentView = sv -> sc ->
                html(head(title("test-server-title")),
                        body(component(new OkState(80000), SUB_STATE_VIEW),
                                component(new OkState(1000), SUB_STATE_VIEW),
                             SUB_STATE_VIEW.apply(sv).apply(sc)
                        ));

        final View<NotFoundState> notFoundComponent =
                sv -> html(headPlain(title("Not found")),
                        body(h1("Not found 404"))).statusCode(404);

        final ComponentView<AppState> appComponent = sv -> sc -> {
            if (sv instanceof NotFoundState) {
                return notFoundComponent.apply((NotFoundState)sv);
            } else if (sv instanceof OkState) {
                return okComponentView.apply((OkState)sv).apply(new NewState.Default<>() {
                    @Override
                    public void set(OkState newState) {
                        sc.set(newState);
                    }
                });
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

        public OkState(final int i) {
            this.i = i;
        }

        public CompletableFuture<AppState> toCompletableFuture() {
            return CompletableFuture.completedFuture(this);
        }
    }

    public static final ComponentView<OkState> SUB_STATE_VIEW = sv -> sc ->
            div(div(button(attr("type", "button"),
                            attr("id", "b0"),
                            text("+1"),
                            on("click",
                                    d -> { sc.set(new OkState(sv.i + 1));}))),
                    div(span(attr("id", "s0"),
                            style("background-color", sv.i % 2 ==0 ? "red" : "blue"),
                            text(sv.i)))
            );
}
