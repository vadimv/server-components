package rsp;

import rsp.server.HttpRequest;
import rsp.services.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class App<S> {
    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";

    public final AppConfig config;
    public final Function<HttpRequest, CompletableFuture<S>> routes;
    public final BiFunction<String, S, String> state2path;
    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();
    public final Component<S> rootComponent;

    public App(AppConfig config,
               Function<HttpRequest, CompletableFuture<S>> routes,
               BiFunction<String, S, String> state2path,
               Component<S> rootComponent) {
        this.config = config;
        this.routes = routes;
        this.state2path = state2path;
        this.rootComponent = rootComponent;
    }

    public App(Function<HttpRequest,
               CompletableFuture<S>> routes,
               Component<S> rootComponent) {
        this(AppConfig.DEFAULT,
             routes,
             (currentPath, s) -> currentPath,
             rootComponent);
    }

    public App(S initialState,
               Component<S> rootComponent) {
        this(AppConfig.DEFAULT,
             request -> CompletableFuture.completedFuture(initialState),
             (currentPath, s) -> currentPath,
             rootComponent);
    }


    public final BiFunction<String, RenderContext, RenderContext> enrichRenderContext() {
        return (sessionId, ctx) ->
                new EnrichingXhtmlContext(ctx,
                                          sessionId,
                                        "/",
                                         DefaultConnectionLostWidget.HTML,
                                         config.heartbeatIntervalMs);
    }

}

