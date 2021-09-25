package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class RouteDefinition<S> {

    private final HttpRequest.HttpMethod httpMethod;
    private final PathPattern pathPattern;
    private final TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun;

    public RouteDefinition(HttpRequest.HttpMethod httpMethod,
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

    public RouteDefinition(HttpRequest.HttpMethod httpMethod,
                           String pathPattern,
                           TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        this.httpMethod = httpMethod;
        this.pathPattern = new PathPattern( pathPattern );
        this.matchFun = matchFun;
    }

    public Function<HttpRequest, Optional<CompletableFuture<? extends S>>> route() {
        return request -> {
            if (httpMethod.equals(request.method) && pathPattern.match(request.path)) {
                return Optional.of(callMatchFun(request));
            } else {
                return Optional.empty();
            }
        };
    }

    private CompletableFuture<S> callMatchFun(HttpRequest request) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes();
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply("",
                                  "",
                                  request);
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < request.path.size();
            return matchFun.apply(request.path.get(pathParameterIndexes[0]),
                                  "",
                                  request);
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < request.path.size();
            return matchFun.apply(request.path.get(pathParameterIndexes[0]),
                                  request.path.get(pathParameterIndexes[1]),
                                  request);
        }
    }

}
