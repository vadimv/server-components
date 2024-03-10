package rsp.component;

import rsp.server.Path;
import rsp.server.http.HttpRequest;
import rsp.server.http.HttpStateOrigin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpComponentDefinition<S> extends StatefulComponentDefinition<S> {

    final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting;
    final ComponentView<S> componentView;

    public HttpComponentDefinition(final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting,
                                   final ComponentView<S> componentView) {
        super(new Object());
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.componentView = Objects.requireNonNull(componentView);
    }

    @Override
    protected BiFunction<S, Path, Path> state2pathFunction() {
        return (__, path) -> path;
    }

    @Override
    protected Function<HttpStateOrigin, CompletableFuture<? extends S>> resolveStateFunction() {
        return httpStateOrigin -> initialStateRouting.apply(httpStateOrigin.httpRequest());
    }

    @Override
    protected ComponentView<S> componentView() {
        return componentView;
    }
}
