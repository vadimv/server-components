package rsp.routing;

import rsp.server.Path;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class PathMatchFunction<S> implements Function<Path, CompletableFuture<S>> {

    private final PathPattern pathPattern;
    private final BiFunction<String, String, CompletableFuture<S>> matchFun;

    public PathMatchFunction(final PathPattern pathPattern,
                             final BiFunction<String, String, CompletableFuture<S>> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public CompletableFuture<S> apply(final Path path) {
        return callMatchFun(path);
    }

    private CompletableFuture<S> callMatchFun(final Path path) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply("", "");
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < path.size();
            return matchFun.apply(path.get(pathParameterIndexes[0]), "");
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < path.size();
            return matchFun.apply(path.get(pathParameterIndexes[0]), path.get(pathParameterIndexes[1]));
        }
    }
}
