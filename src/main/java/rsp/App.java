package rsp;

import rsp.component.*;
import rsp.jetty.WebServer;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderedPage;
import rsp.routing.Routing;
import rsp.server.http.HttpRequest;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An assembly point for everything needed to set off a UI application.
 * This class object itself to be provided to a hosting web container, for example {@link WebServer}.
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public final class App<S> {

    /**
     * The default rate of heartbeat messages from a browser to server.
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10000;

    /**
     * The application's rate of heartbeat messages from a browser to server.
     */
    public final int heartbeatIntervalMs;

    /**
     * The root of the components tree.
     */
    public final StatefulComponentDefinition<S> rootComponentDefinition;

    public final Map<QualifiedSessionId, RenderedPage<S>> pagesStorage = new ConcurrentHashMap<>();

    /**
     * Creates an instance of an application.
     * @param rootComponentDefinition the root of the components tree
     * @param heartbeatIntervalMs The application's rate of heartbeat messages from a browser to server
     */
    public App(final StatefulComponentDefinition<S> rootComponentDefinition,
               final int heartbeatIntervalMs) {
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }
    /**
     * Creates an instance of an application.
     * @param rootComponentDefinition the root of the components tree
     */
    public App(final StatefulComponentDefinition<S> rootComponentDefinition) {
        this(rootComponentDefinition, DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routing a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponentView the root of the components tree
     */
    public App(final Routing<HttpRequest, S> routing,
               final ComponentView<S> rootComponentView) {
        this(new HttpRequestStateComponentDefinition<>(routing, rootComponentView), DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot as a CompletableFuture
     * @param rootComponentView the root of the components tree
     */
    public App(final CompletableFuture<S> initialState,
               final ComponentView<S> rootComponentView) {
        this(new InitialStateComponentDefinition<>(initialState, rootComponentView), DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot
     * @param rootComponentView the root of the components tree
     */
    public App(final S initialState,
               final ComponentView<S> rootComponentView) {
        this(CompletableFuture.completedFuture(initialState), rootComponentView);
    }


    public App(final S initialState,
               final View<S> rootComponentView) {
        this(new InitialStateComponentDefinition<>(initialState,
                                                   state -> newState -> rootComponentView.apply(state)),
             DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    public App(final Routing<HttpRequest, S> routing,
               final View<S> rootComponentView) {
        this(new HttpRequestStateComponentDefinition<>(routing,
                                                       state -> newState -> rootComponentView.apply(state)),
                DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

}

