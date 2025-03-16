package rsp.component;

import rsp.routing.Routing;
import rsp.server.http.HttpRequest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class HttpRequestStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting;
    private final ComponentView<S> componentView;

    public HttpRequestStateComponentDefinition(final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting,
                                               final ComponentView<S> componentView) {
        super(HttpRequestStateComponentDefinition.class);
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.componentView = Objects.requireNonNull(componentView);
    }

    public HttpRequestStateComponentDefinition(final Routing<HttpRequest, S> routing,
                                               final View<S> view) {
        this(routing,
                componentView(view));

    }

    private static <S> ComponentView<S> componentView(final View<S> rootComponentView) {
        return newState -> state -> rootComponentView.apply(state);
    }


    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, httpStateOrigin) -> initialStateRouting.apply(httpStateOrigin.httpRequest());
    }

    @Override
    protected ComponentView<S> componentView() {
        return componentView;
    }
}
