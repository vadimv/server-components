package rsp.page;

import rsp.server.Path;

import java.util.function.BiFunction;

public final class StateToRouteDispatch<S> {
    public final Path basePath;
    public final BiFunction<Path, S, Path> stateToPath;

    public StateToRouteDispatch(Path basePath, BiFunction<Path, S, Path> stateToPath) {
        this.basePath = basePath;
        this.stateToPath = stateToPath;
    }
}
