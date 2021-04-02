package rsp;

import rsp.page.*;
import rsp.server.HttpRequest;
import rsp.server.Path;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * An assembly point for everything needed to set off a UI application.
 * This class object itself to be provided to a hosting web container, for example {@link rsp.jetty.JettyServer}.
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public final class App<S> {
    /**
     * The application's configuration.
     */
    public final AppConfig config;

    /**
     * A function that dispatches an incoming HTTP request to a page's initial state.
     */
    public final Function<HttpRequest, CompletableFuture<S>> routes;

    /**
     * A function that dispatches a current state snapshot to the browser's navigation bar's path.
     */
    public final Function<S, Path> state2path;

    /**
     * The root of the components tree.
     */
    public final Render<S> rootComponent;

    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param config an application config
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param state2path a function that dispatches a current state snapshot to the browser's navigation bar's path
     * @param rootComponent the root of the components tree
     */
    public App(AppConfig config,
               Function<HttpRequest, CompletableFuture<S>> routes,
               Function<S, Path> state2path,
               Render<S> rootComponent) {
        this.config = config;
        this.routes = routes;
        this.state2path = state2path;
        this.rootComponent = rootComponent;
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponent the root of the components tree
     */
    public App(Function<HttpRequest, CompletableFuture<S>> routes,
               Render<S> rootComponent) {
        this(AppConfig.DEFAULT,
             routes,
             (s) -> Path.EMPTY_ABSOLUTE,
             rootComponent);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponent the root of the components tree
     */
    public App(S initialState,
               Render<S> rootComponent) {
        this(AppConfig.DEFAULT,
             request -> CompletableFuture.completedFuture(initialState),
             (s) ->  Path.EMPTY_ABSOLUTE,
             rootComponent);
    }
}

