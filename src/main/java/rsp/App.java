package rsp;

import rsp.server.HttpRequest;
import rsp.services.PageRendering;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class App<S> {
    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";
    public static final int HEARTBEAT_INTERVAL_MS = 5000;

    public final Function<HttpRequest, CompletableFuture<S>> routes;
    public final BiFunction<String, S, String> state2path;
    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();
    public final Component<S> rootComponent;

    public App(Function<HttpRequest, CompletableFuture<S>> routes,
               BiFunction<String, S, String> state2path,
               Component<S> rootComponent) {
        this.routes = routes;
        this.state2path = state2path;
        this.rootComponent = rootComponent;
    }

    public App(Function<HttpRequest, CompletableFuture<S>> routes,
               Component<S> rootComponent) {
        this(routes,
            (currentPath, s) -> currentPath,
            rootComponent);
    }

    public App(S initialState,
               Component<S> rootComponent) {
        this(request -> CompletableFuture.completedFuture(initialState),
             (currentPath, s) -> currentPath,
             rootComponent);
    }

    public final BiFunction<String, RenderContext<S>, RenderContext<S>> enrich = (sessionId,ctx) ->
            new EnrichingXhtmlContext<>(ctx,
                                        sessionId,
                                        "/",
                                        DefaultConnectionLostWidget.HTML,
                                        HEARTBEAT_INTERVAL_MS);

}

