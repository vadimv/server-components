package rsp.routing;

import rsp.server.http.HttpRequest;
import rsp.util.TriFunction;

import java.util.function.Function;

public final class HttpRequestMatchFunction<S> implements Function<HttpRequest, S> {

    private final PathPattern pathPattern;
    private final TriFunction<HttpRequest, String, String, S> matchFun;

    public HttpRequestMatchFunction(final PathPattern pathPattern,
                                    final TriFunction<HttpRequest, String, String, S> matchFun) {
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }

    @Override
    public S apply(final HttpRequest httpRequest) {
        return callMatchFun(httpRequest);
    }

    private S callMatchFun(final HttpRequest request) {
        final int[] pathParameterIndexes = pathPattern.paramsIndexes;
        if (pathParameterIndexes.length == 0) {
            return matchFun.apply(request, "", "");
        } else if (pathParameterIndexes.length == 1) {
            assert pathParameterIndexes[0] < request.path.elementsCount();
            return matchFun.apply(request, request.path.get(pathParameterIndexes[0]), "");
        } else {
            assert pathParameterIndexes[0] < pathParameterIndexes[1];
            assert pathParameterIndexes[1] < request.path.elementsCount();
            return matchFun.apply(request, request.path.get(pathParameterIndexes[0]), request.path.get(pathParameterIndexes[1]));
        }
    }
}
