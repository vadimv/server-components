package rsp.examples;

import rsp.App;
import rsp.Component;
import rsp.Page;
import rsp.QualifiedSessionId;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import rsp.jetty.JettyServer;
import rsp.server.HttpRequest;
import rsp.services.PageRendering;
import rsp.dsl.Html;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class JettyBasic {

    public static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) throws Exception {
        final int p = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        final Component<Integer> c = useState -> {
            return Html.div(Html.event("click", d -> { useState.accept(useState.get() + 1);}));
        };

        final Component<Integer> render = useState -> {
                final var state = useState.get();
                return Html.html( Html.body(
                                   Html.div(Html.span( Html.style("background-color", useState.get() %2 ==0 ? "red" : "blue"),  Html.text(state.toString()))),
                                     Html.event("click",
                                                    d -> { useState.accept(useState.get() + 1);})
                ));
        };

        final Function<HttpRequest, Integer> routes = request -> {
            if (path(request, "/1")) return 1;
            else if (path(request, "/2")) return 2;
            else return -1;
        };

        final BiFunction<String, Integer, String> state2path = (currentPath, newState) -> {
            switch (newState) {
                case 1: return "/1";
                case 2: return "/1";
            }
            return "/1";
        };
        final Map<QualifiedSessionId, Page<Integer>> pagesStorage = new ConcurrentHashMap<>();
        final var s = new JettyServer(new App<>(p,"", routes.andThen(v -> CompletableFuture.completedFuture(v)), state2path, pagesStorage, render));
        s.start();
        s.join();
    }

    private static boolean path(HttpRequest request, String s) {
        return request.path.equals(s);
    }
}
