package rsp.browserautomation;

import rsp.App;
import rsp.Component;
import rsp.jetty.JettyServer;
import rsp.page.PageLifeCycle;
import rsp.page.QualifiedSessionId;
import rsp.server.HttpRequest;
import rsp.state.UseState;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static rsp.dsl.Html.*;

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
        final Component<State> render = state ->
                html(head(title("test-server-title")),
                     body(subComponent.render(state.get().i, s -> state.accept(new State(s))),
                           div(button(attr("type", "button"),
                                      attr("id", "b0"),
                                      text("+1"),
                               on("click",
                                  d -> { state.accept(new State(state.get().i + 1));}))),
                           div(span(attr("id", "s0"),
                                    style("background-color", state.get().i % 2 ==0 ? "red" : "blue"),
                                    text(state.get().i)))
        ));

        final Function<HttpRequest, CompletableFuture<State>> routes = request -> request.path.createMatcher(new State(-1))
                .match(s -> request.method == HttpRequest.Methods.GET,
                       s -> new State(Integer.parseInt(s)).toCompletableFuture())
                .result;

        final PageLifeCycle<State> plc = new PageLifeCycle.Default<>() {
            @Override
            public void beforeLivePageCreated(QualifiedSessionId sid, UseState<State> useState) {
                final Thread t = new Thread(() -> {
                    while (true)
                    try {
                        Thread.sleep(2000);
                        synchronized (useState) {
                            useState.accept(new State(useState.get().i + 1));
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                t.start();
            }
        };

        final App<State> app = new App<>(routes,
                                         new PageLifeCycle.Default<>(),
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

        public CompletableFuture<State> toCompletableFuture() {
            return CompletableFuture.completedFuture(this);
        }
    }

    public static final Component<Integer> subComponent = state ->
            div(attr("id", "d0"),
                text("+10"),
                on("click", d -> { state.accept(state.get() + 10);}));
}
