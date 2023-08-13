package rsp;

import rsp.component.HttpRequestStatefulComponentDefinition;
import rsp.page.PageLifeCycle;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.routing.Routing;
import rsp.server.http.HttpRequest;
import rsp.component.ComponentView;
import rsp.component.View;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.component.ComponentDsl.webComponent;

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
     * An implementation of the lifecycle events listener.
     */
    public final PageLifeCycle<S> lifeCycleEventsListener;

    /**
     * The root of the components tree.
     */
    public final HttpRequestStatefulComponentDefinition<S> rootComponent;

    public final Map<QualifiedSessionId, RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param config an application config
     * @param lifeCycleEventsListener a listener for the app pages lifecycle events
     * @param rootComponent the root of the components tree
     */
    private App(final AppConfig config,
                final PageLifeCycle<S> lifeCycleEventsListener,
                final HttpRequestStatefulComponentDefinition<S> rootComponent) {
        this.config = Objects.requireNonNull(config);
        this.lifeCycleEventsListener = Objects.requireNonNull(lifeCycleEventsListener);
        this.rootComponent = Objects.requireNonNull(rootComponent);
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routing a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponentView the root of the components tree
     */
    public App(final Routing<HttpRequest, S> routing,
               final ComponentView<S> rootComponentView) {
        this(AppConfig.DEFAULT,
             new PageLifeCycle.Default<>(),
             webComponent(routing,
                          (__, path) -> path,
                          rootComponentView));
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot as a CompletableFuture
     * @param rootComponentView the root of the components tree
     */
    public App(final CompletableFuture<S> initialState,
               final ComponentView<S> rootComponentView) {
        this(AppConfig.DEFAULT,
             new PageLifeCycle.Default<>(),
             webComponent(request -> initialState,
                          (__, path) ->  path,
                          rootComponentView));
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponentView the root of the components tree
     */
    public App(final S initialState,
               final ComponentView<S> rootComponentView) {
        this(CompletableFuture.completedFuture(initialState),
             rootComponentView);
    }


    public App(final S initialState,
               final View<S> rootComponentView) {
        this(AppConfig.DEFAULT,
             new PageLifeCycle.Default<>(),
             webComponent(request -> CompletableFuture.completedFuture(initialState),
                          (__, path) -> path,
                          state -> newState -> rootComponentView.apply(state)));
    }

    public App(final Routing<HttpRequest, S> routing,
               final View<S> rootComponentView) {
        this(AppConfig.DEFAULT,
             new PageLifeCycle.Default<>(),
             webComponent(routing,
                          (__, path) ->  path,
                          state -> newState -> rootComponentView.apply(state)));
    }

    /**
     * Sets the application's config.
     * @param config an application config
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> config(final AppConfig config) {
        return new App<>(config,  this.lifeCycleEventsListener, this.rootComponent);
    }


    /**
     * Sets a listener for the app pages lifecycle events.
     * @see PageLifeCycle
     *
     * @param lifeCycleEventsListener the listener interface for receiving page lifecycle events.
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> pageLifeCycle(final PageLifeCycle<S> lifeCycleEventsListener) {
        return new App<>(this.config, lifeCycleEventsListener, this.rootComponent);
    }
}

