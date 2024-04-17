package rsp.component;

import rsp.server.Path;
import rsp.server.http.Fragment;
import rsp.server.http.Query;
import rsp.server.http.RelativeUrl;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class PathStateComponentDefinition<S> extends RelativeUrlStateComponentDefinition<S> {
    private final Function<Path, CompletableFuture<? extends S>> initialStateRouting;
    private final BiFunction<S, Path, Path> stateToPath;
    private final ComponentView<S> componentView;

    public PathStateComponentDefinition(Function<Path, CompletableFuture<? extends S>> initialStateRouting,
                                        BiFunction<S, Path, Path> stateToPath, ComponentView<S> componentView) {
        super(PathStateComponentDefinition.class);
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.stateToPath = Objects.requireNonNull(stateToPath);
        this.componentView = Objects.requireNonNull(componentView);
    }

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return (key, httpStateOrigin) -> initialStateRouting.apply(httpStateOrigin.relativeUrl().path());
    }

    @Override
    protected BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl() {
        return (s, r) -> new RelativeUrl(stateToPath.apply(s, r.path()), Query.EMPTY, Fragment.EMPTY);
    }

    @Override
    protected Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToState() {
        return relativeUrl -> initialStateRouting.apply(relativeUrl.path());
    }

    @Override
    protected ComponentView<S> componentView() {
        return componentView;
    }
}
