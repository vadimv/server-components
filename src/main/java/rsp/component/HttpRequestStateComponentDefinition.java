package rsp.component;

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

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, httpStateOrigin) -> initialStateRouting.apply(httpStateOrigin.httpRequest());
    }

    @Override
    protected ComponentView<S> componentView() {
        return componentView;
    }
}
