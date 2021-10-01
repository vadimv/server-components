package rsp;

import rsp.page.*;
import rsp.server.HttpRequest;
import rsp.server.Path;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
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
    public final Function<HttpRequest, Optional<CompletableFuture<? extends S>>> routes;

    /**
     * A function that dispatches its first argument, a current state snapshot
     * and its second argument of the previous path to the browser's navigation bar's path.
     */
    public final BiFunction<S, Path, Path> state2path;

    /**
     * An implementation of the lifecycle events listener.
     */
    public final PageLifeCycle<S> lifeCycleEventsListener;

    /**
     * The root of the components tree.
     */
    public final Component<? extends S> rootComponent;

    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param config an application config
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param state2path a function that dispatches a current state snapshot to the browser's navigation bar's path
     * @param lifeCycleEventsListener a listener for the app pages lifecycle events
     * @param rootComponent the root of the components tree
     */
    public App(AppConfig config,
               Function<HttpRequest, Optional<CompletableFuture<? extends S>>> routes,
               BiFunction<S, Path, Path> state2path,
               PageLifeCycle<S> lifeCycleEventsListener,
               Component<? extends S> rootComponent) {
        this.config = config;
        this.routes = routes;
        this.state2path = state2path;
        this.lifeCycleEventsListener = lifeCycleEventsListener;
        this.rootComponent = rootComponent;
    }

    /**
     * Creates an instance of an application.
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param lifeCycleEventsListener a listener for the app pages lifecycle events
     * @param rootComponent the root of the components tree
     */
    public App(Function<HttpRequest, Optional<CompletableFuture<? extends S>>> routes,
               PageLifeCycle<S> lifeCycleEventsListener,
               Component<? extends S> rootComponent) {
        this(AppConfig.DEFAULT,
             routes,
             (s, p) -> p,
             lifeCycleEventsListener,
             rootComponent);
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponent the root of the components tree
     */
    public App(Function<HttpRequest, Optional<CompletableFuture<? extends S>>> routes,
               Component<? extends S> rootComponent) {
        this(AppConfig.DEFAULT,
             routes,
             (s, p) -> p,
             new PageLifeCycle.Default<>(),
             rootComponent);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponent the root of the components tree
     */
    public App(S initialState,
               Component<? extends S> rootComponent,
               PageLifeCycle<S> lifeCycleEventsListener) {
        this(AppConfig.DEFAULT,
             request -> Optional.of(CompletableFuture.completedFuture(initialState)),
             (s, p) ->  p,
             lifeCycleEventsListener,
             rootComponent);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponent the root of the components tree
     */
    public App(S initialState,
               Component<? extends S> rootComponent) {
        this(AppConfig.DEFAULT,
                request -> Optional.of(CompletableFuture.completedFuture(initialState)),
                (s, p) ->  p,
                new PageLifeCycle.Default<>(),
                rootComponent);
    }
}

