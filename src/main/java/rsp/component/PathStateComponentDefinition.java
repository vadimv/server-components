package rsp.component;

import rsp.server.Path;
import rsp.server.http.*;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class PathStateComponentDefinition<S> extends RelativeUrlStateComponentDefinition<S> {
    private final Function<Path, S> initialStateRouting;
    private final BiFunction<S, Path, Path> stateToPath;
    private final ComponentView<S> componentView;

    private final Path path;

    public PathStateComponentDefinition(final RelativeUrl relativeUrl,
                                        final Function<Path, S> initialStateRouting,
                                        final BiFunction<S, Path, Path> stateToPath,
                                        final ComponentView<S> componentView) {
        super(PathStateComponentDefinition.class, relativeUrl);
        this.path = relativeUrl.path();
        this.initialStateRouting = Objects.requireNonNull(initialStateRouting);
        this.stateToPath = Objects.requireNonNull(stateToPath);
        this.componentView = Objects.requireNonNull(componentView);
    }

    @Override
    protected ComponentStateSupplier<S> stateSupplier() {
        return key -> initialStateRouting.apply(path);
    }

    @Override
    protected BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl() {
        return (s, relativeUrl) -> new RelativeUrl(stateToPath.apply(s, relativeUrl.path()), Query.EMPTY, Fragment.EMPTY);
    }

    @Override
    protected Function<RelativeUrl, S> relativeUrlToState() {
        return relativeUrl -> initialStateRouting.apply(relativeUrl.path());
    }

    @Override
    protected ComponentView<S> componentView() {
        return componentView;
    }
}
