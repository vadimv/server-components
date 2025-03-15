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
     * The root of the components tree.
     */
    public final StatefulComponentDefinition<S> rootComponentDefinition;

    /**
     * Creates an instance of an application.
     * @param rootComponentDefinition the root of the components tree
     */
    public App(final StatefulComponentDefinition<S> rootComponentDefinition) {
        this.rootComponentDefinition = Objects.requireNonNull(rootComponentDefinition);
    }

    /**
     * Creates an instance of an application with the default configuration.
     * @param routing a function that dispatches an incoming HTTP request to a page's initial state
     * @param rootComponentView the root of the components tree
     */
    public App(final Routing<HttpRequest, S> routing,
               final ComponentView<S> rootComponentView) {
        this(new HttpRequestStateComponentDefinition<>(routing, rootComponentView));
    }

    /**
     * Creates an instance of an application with the default config
     * and default routing which maps any request to the initial state.
     * @param initialState the initial state snapshot as a CompletableFuture
     * @param rootComponentView the root of the components tree
     */
    public App(final CompletableFuture<S> initialState,
               final ComponentView<S> rootComponentView) {
        this(new InitialStateComponentDefinition<>(initialState, rootComponentView));
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
                                                   state -> newState -> rootComponentView.apply(state)));
    }

    public App(final Routing<HttpRequest, S> routing,
               final View<S> rootComponentView) {
        this(new HttpRequestStateComponentDefinition<>(routing,
                                                       state -> newState -> rootComponentView.apply(state)));
    }

}

