package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;
import rsp.util.data.Tuple2;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public final class RouteDefinition<S> {

    private final Predicate<HttpRequest> predicate;
    private final Tuple2<PathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>>> matchFun;
  /*  public RouteDefinition(HttpRequest.HttpMethod httpMethod,
                           String pathPattern,
                           Function<HttpRequest,
                           CompletableFuture<S>> matchFun) {
        this(httpMethod, pathPattern, (p1, p2, req) -> matchFun.apply(req));
    }

    public RouteDefinition(HttpRequest.HttpMethod httpMethod,
                           String pathPattern,
                           BiFunction<String, HttpRequest,
                           CompletableFuture<S>> matchFun) {
        this(httpMethod, pathPattern, (p1, p2, req) -> matchFun.apply(p1, req));
    }
*/
    public RouteDefinition(Predicate<HttpRequest> predicate,
                           Tuple2<PathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>>> matchFun) {
        this.predicate = predicate;
        this.matchFun = matchFun;
    }

    public boolean test(HttpRequest request) {
        return predicate.test(request);
    }

    public Function<HttpRequest, CompletableFuture<? extends S>> route() {
        return request -> callMatchFun(request);
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
