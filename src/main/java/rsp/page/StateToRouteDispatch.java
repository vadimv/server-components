package rsp.page;

import rsp.server.Path;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class StateToRouteDispatch<S> {
    public final Path basePath;
    public final Function<S, Path> stateToPath;

    public StateToRouteDispatch(Path basePath, Function<S, Path> stateToPath) {
        this.basePath = basePath;
        this.stateToPath = stateToPath;
    }
}
