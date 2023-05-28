package rsp.page;

import rsp.server.Path;

import java.util.function.BiFunction;

public final class StateToRouteDispatch<S> {
    public final Path basePath;
    public final BiFunction<S, Path, Path> stateToPath;

    public StateToRouteDispatch(final Path basePath, final BiFunction<S, Path, Path> stateToPath) {
        this.basePath = basePath;
        this.stateToPath = stateToPath;
    }
}
