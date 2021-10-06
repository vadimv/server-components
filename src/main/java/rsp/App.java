package rsp;

import rsp.page.*;
import rsp.routing.Route;
import rsp.server.HttpRequest;
import rsp.server.Path;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

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
    public final Route<HttpRequest, S> routes;

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
    public final Component<S> rootComponent;

    public final Map<QualifiedSessionId, PageRendering.RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param config an application config
     * @param state2path a function that dispatches a current state snapshot to the browser's navigation bar's path
     * @param lifeCycleEventsListener a listener for the app pages lifecycle events
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponent the root of the components tree
     */
    private App(AppConfig config,
                BiFunction<S, Path, Path> state2path,
                PageLifeCycle<S> lifeCycleEventsListener,
                Route<HttpRequest, S> routes,
                Component<S> rootComponent) {
        this.config = config;
        this.routes = routes;
        this.state2path = state2path;
        this.lifeCycleEventsListener = lifeCycleEventsListener;
        this.rootComponent = rootComponent;
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routes a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponent the root of the components tree
     */
    public App(Route<HttpRequest, S> routes,
               Component<S> rootComponent) {
        this(AppConfig.DEFAULT,
             (s, p) -> p,
             new PageLifeCycle.Default<>(),
             routes,
             rootComponent);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponent the root of the components tree
     */
    public App(S initialState,
               Component<S> rootComponent) {
        this(AppConfig.DEFAULT,
             (s, p) ->  p,
             new PageLifeCycle.Default<>(),
             request -> Optional.of(CompletableFuture.completedFuture(initialState)),
             rootComponent);
    }

    /**
     * Sets the application's config.
     * @param config an application config
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> config(AppConfig config) {
        return new App<>(config, this.state2path, this.lifeCycleEventsListener, this.routes, this.rootComponent);
    }

    /**
     * Sets the application's global state to the browser's navigation path function.
     * @param stateToPath a function that dispatches a current state snapshot to the browser's navigation bar's path
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> stateToPath(BiFunction<S, Path, Path> stateToPath) {
        return new App<>(this.config, stateToPath, this.lifeCycleEventsListener, this.routes, this.rootComponent);
    }

    /**
     * Sets a listener for the app pages lifecycle events.
     * @see PageLifeCycle
     *
     * @param lifeCycleEventsListener the listener interface for receiving page lifecycle events.
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> pageLifeCycle(PageLifeCycle<S> lifeCycleEventsListener) {
        return new App<>(this.config, this.state2path, lifeCycleEventsListener, this.routes, this.rootComponent);
    }
}

