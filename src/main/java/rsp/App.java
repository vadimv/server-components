package rsp;

import rsp.component.HttpComponentDefinition;
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
    public final HttpComponentDefinition<S> rootComponentDefinition;

    public final Map<QualifiedSessionId, RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param config an application config
     * @param lifeCycleEventsListener a listener for the app pages lifecycle events
     * @param rootComponentDefinition the root of the components tree
     */
    private App(final AppConfig config,
                final PageLifeCycle<S> lifeCycleEventsListener,
                final HttpComponentDefinition<S> rootComponentDefinition) {
        this.config = Objects.requireNonNull(config);
        this.lifeCycleEventsListener = Objects.requireNonNull(lifeCycleEventsListener);
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
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
             new HttpComponentDefinition(routing,
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
             new HttpComponentDefinition<>(request -> initialState,
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
             new HttpComponentDefinition<>(request -> CompletableFuture.completedFuture(initialState),
                                           state -> newState -> rootComponentView.apply(state)));
    }

    public App(final Routing<HttpRequest, S> routing,
               final View<S> rootComponentView) {
        this(AppConfig.DEFAULT,
             new PageLifeCycle.Default<>(),
             new HttpComponentDefinition<>(routing,
                                           state -> newState -> rootComponentView.apply(state)));
    }

    /**
     * Sets the application's config.
     * @param config an application config
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> withConfig(final AppConfig config) {
        return new App<>(config,  this.lifeCycleEventsListener, this.rootComponentDefinition);
    }


    /**
     * Sets a listener for the app pages lifecycle events.
     * @see PageLifeCycle
     *
     * @param lifecycleEventsListener the listener interface for receiving page lifecycle events.
     * @return a new application object with the same field values except of the provided field
     */
    public App<S> withPageLifecycle(final PageLifeCycle<S> lifecycleEventsListener) {
        return new App<>(this.config, lifecycleEventsListener, this.rootComponentDefinition);
    }
}

