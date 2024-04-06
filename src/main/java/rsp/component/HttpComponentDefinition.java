package rsp.component;

import rsp.server.Path;
import rsp.server.http.HttpRequest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HttpComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting;
    private final ComponentView<S> componentView;

    public HttpComponentDefinition(final Function<HttpRequest, CompletableFuture<? extends S>> initialStateRouting,
                                   final ComponentView<S> componentView) {
        super("http-component");
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.componentView = Objects.requireNonNull(componentView);
    }

    protected BiFunction<S, Path, Path> state2pathFunction() {
        return (__, path) -> path;
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
    protected BeforeRenderCallback<S> beforeRenderCallback() {
        return (key, state, newState, beforeRenderCallback) -> {
        };
    }

    @Override
    protected StateAppliedCallback<S> afterStateAppliedCallback() {
        return (key, state, ctx) -> {};
    }

    @Override
    protected UnmountCallback<S> unmountCallback() {
        return (key, state) -> System.out.println("Unmounted: " + key);
    }
}
