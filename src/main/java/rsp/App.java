package rsp;

import rsp.server.HttpRequest;
import rsp.page.*;
import rsp.server.Path;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An this class is an assembly point for everything needed to set off a UI application
 * This class object itself to be provided to a hosting web container {@link rsp.jetty.JettyServer}
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public final class App<S> {
    /**
     * The web socket endpoint path
     */
    public static final String WS_ENDPOINT_PATH = "/bridge/web-socket/{pid}/{sid}";

    /**
     * The application's config
     */
    public final AppConfig config;

    /**
     * A function that dispatches an incoming HTTP request to a page's initial state
     */
    public final Function<HttpRequest, CompletableFuture<S>> routes;

    /**
     * A function that dispatches a current state snapshot to the browser's navigation bar's path
     */
    public final Function<S, Path> state2path;

    /**
     * The root of the components tree
     */
    public final Component<S> rootComponent;

    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();


    public App(AppConfig config,
               Function<HttpRequest, CompletableFuture<S>> routes,
               Function<S, Path> state2path,
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
             (s) -> Path.EMPTY_ABSOLUTE,
             rootComponent);
    }

    public App(S initialState,
               Component<S> rootComponent) {
        this(AppConfig.DEFAULT,
             request -> CompletableFuture.completedFuture(initialState),
             (s) ->  Path.EMPTY_ABSOLUTE,
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

