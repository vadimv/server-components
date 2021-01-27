package rsp.examples;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;
import rsp.state.UseState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class JettyBasic {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final int p = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        final Component<State> render = state ->
                html(body(subComponent.render(UseState.readWrite(() -> state.get().i, s -> state.accept(new State(s)))),
                           div(span(text("+1")),
                               on("click",
                                  d -> { state.accept(new State(state.get().i + 1));})),
                           div(span(style("background-color", state.get().i % 2 ==0 ? "red" : "blue"), text(state.get().i)))
        ));

        final Function<HttpRequest, State> routes = request -> {
            if (path(request, "/1")) return new State(1);
            else if (path(request, "/2")) return new State(2);
            else return new State(-1);
        };

        final App<State> app = new App<>(routes.andThen(v -> CompletableFuture.completedFuture(v)),
                                         render);
        final var s = new JettyServer(p,
                              "", app);
        s.start();
        s.join();
    }

    private static boolean path(HttpRequest request, String s) {
        return request.path.equals(s);
    }

    private static class State {
        private final int i;

        public State(int i) {
            this.i = i;
        }
    }

    public static final Component<Integer> subComponent = state ->
            div(text("+10"),
                on("click", d -> { state.accept(state.get() + 10);}));
}
