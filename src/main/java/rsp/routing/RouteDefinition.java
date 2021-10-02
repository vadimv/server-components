package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;
import rsp.util.data.Tuple2;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class RouteDefinition<S> implements Route<HttpRequest, S>
{

    private final Predicate<HttpRequest> predicate;
    private final Tuple2<PathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>>> matchFun;

    public RouteDefinition(Predicate<HttpRequest> predicate,
                           Tuple2<PathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>>> matchFun) {
        this.predicate = predicate;
        this.matchFun = matchFun;
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(HttpRequest request) {
        if (predicate.test(request)) {
            return Optional.of(callMatchFun(request));
        }
        return Optional.empty();
    }

    public boolean test(HttpRequest request) {
        return predicate.test(request);
    }

    private CompletableFuture<S> callMatchFun(HttpRequest request) {
        final int[] pathParameterIndexes = matchFun._1.paramsIndexes;;
        if (pathParameterIndexes.length == 0) {
            return matchFun._2.apply("",
                                      "",
                                      request);
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < request.path.size();
            return matchFun._2.apply(request.path.get(pathParameterIndexes[0]),
                                      "",
                                      request);
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < request.path.size();
            return matchFun._2.apply(request.path.get(pathParameterIndexes[0]),
                                      request.path.get(pathParameterIndexes[1]),
                                      request);
        }
    }


}
