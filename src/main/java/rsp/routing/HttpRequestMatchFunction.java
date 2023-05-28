package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class HttpRequestMatchFunction<S> implements Function<HttpRequest, CompletableFuture<S>> {

    private final PathPattern pathPattern;
    private final TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun;

    public HttpRequestMatchFunction(final PathPattern pathPattern,
                                    final TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public CompletableFuture<S> apply(final HttpRequest httpRequest) {
        return callMatchFun(httpRequest);
    }

    private CompletableFuture<S> callMatchFun(final HttpRequest request) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply(request, "", "");
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < request.path.size();
            return matchFun.apply(request, request.path.get(pathParameterIndexes[0]), "");
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < request.path.size();
            return matchFun.apply(request, request.path.get(pathParameterIndexes[0]), request.path.get(pathParameterIndexes[1]));
        }
    }
}
