package rsp.browserautomation;

import rsp.App;
import rsp.Component;
import rsp.Rendering;
import rsp.dsl.ComponentDefinition;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static rsp.dsl.Html.*;

public class SimpleServer {

    public static final int PORT = 8085;

    public final JettyServer jetty;

    public SimpleServer(JettyServer jetty) {
        this.jetty = jetty;
    }

    public static void main(String[] args) throws Exception {
        run(true);
    }

    public static SimpleServer run(boolean blockCurrentThread) throws Exception {
        final Component<State> render = state ->
                html(head(title("test-server-title")),
                     body(rwComponent(subComponent, state.get().i, s -> new State(s)),
                          div(button(attr("type", "button"),
                                      attr("id", "b0"),
                                      text("+1"),
                               on("click",
                                  d -> { state.accept(new State(state.get().i + 1));}))),
                           div(span(attr("id", "s0") ,
                                    style("background-color", state.get().i % 2 ==0 ? "red" : "blue"),
                                    text(state.get().i))
                           )
        ));

        final Function<HttpRequest, State> routes = request -> {
            if (path(request, "/1")) return new State(1);
            else if (path(request, "/2")) return new State(2);
            else return new State(-1);
        };

        final App<State> app = new App<>(routes.andThen(v -> CompletableFuture.completedFuture(v)),
                                         render);
        final SimpleServer s = new SimpleServer(new JettyServer(PORT, "", app));
        s.jetty.start();
        if (blockCurrentThread) {
            s.jetty.join();
        }
        return s;
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

    public static final Rendering<Integer> subComponent = state ->
            div(attr("id", "d0"),
                text("+10"),
                on("click", d -> { d.setState(state + 10);}));

    public static <S1, S2> ComponentDefinition<S1, S2> rwComponent(Rendering<S2> component, S2 state, Function<S2, S1> f) {
        return new ComponentDefinition<>(component, state, f);
    }
}
