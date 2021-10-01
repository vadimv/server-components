package rsp.routing;

import rsp.server.Path;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class PathRouteDefinition<S> implements Route<Path, S>, Predicate<Path> {

    public final PathPattern pathPattern;
    public final BiFunction<String, String, CompletableFuture<S>> matchFun;

    public PathRouteDefinition(PathPattern pathPattern,
                               BiFunction<String, String, CompletableFuture<S>> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(Path path) {
        if (pathPattern.match(path)) {
            return Optional.of(callMatchFun(path));
        }
        return Optional.empty();
    }

    private CompletableFuture<S> callMatchFun(Path path) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;;
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply("",
                                      "");
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < path.size();
            return matchFun.apply(path.get(pathParameterIndexes[0]),
                                      "");
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < path.size();
            return matchFun.apply(path.get(pathParameterIndexes[0]),
                                      path.get(pathParameterIndexes[1]));
        }
    }

    @Override
    public boolean test(Path path) {
        return pathPattern.match(path);
    }
}
