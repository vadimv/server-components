package rsp.page;

import java.util.function.BiFunction;

public final class StateToRouteDispatch<S> {
    public final String basePath;
    public final BiFunction<String, S, String> stateToPath;

    public StateToRouteDispatch(String basePath, BiFunction<String, S, String> stateToPath) {
        this.basePath = basePath;
        this.stateToPath = stateToPath;
    }
}
