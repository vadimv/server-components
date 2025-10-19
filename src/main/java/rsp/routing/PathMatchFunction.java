package rsp.routing;

import rsp.server.Path;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class PathMatchFunction<S> implements Function<Path, S> {

    private final PathPattern pathPattern;
    private final BiFunction<String, String, S> matchFun;

    public PathMatchFunction(final PathPattern pathPattern,
                             final BiFunction<String, String, S> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public S apply(final Path path) {
        return callMatchFun(path);
    }

    // TODO
    private S callMatchFun(final Path path) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply("", "");
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < path.elementsCount();
            return matchFun.apply(path.get(pathParameterIndexes[0]), "");
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < path.elementsCount();
            return matchFun.apply(path.get(pathParameterIndexes[0]), path.get(pathParameterIndexes[1]));
        }
    }
}
