package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class HttpRequestMatchFunction<S> implements Function<HttpRequest, CompletableFuture<S>> {

    private final PathPattern pathPattern;
    private final TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun;

    public HttpRequestMatchFunction(PathPattern pathPattern,
                                    TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public CompletableFuture<S> apply(HttpRequest httpRequest) {
        return callMatchFun(httpRequest);
    }

    private CompletableFuture<S> callMatchFun(HttpRequest request) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;
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
