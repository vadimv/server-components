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
        super("http-component");
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

    @Override
    protected MountCallback<S> componentDidMount() {
        return (key, state, newState, beforeRenderCallback) -> {};
    }

    @Override
    protected StateAppliedCallback<S> componentDidUpdate() {
        return (key, state, ctx) -> {};
    }

    @Override
    protected UnmountCallback<S> componentWillUnmount() {
        return (key, state) -> {};
    }
}
