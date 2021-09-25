package rsp.routing;


import rsp.server.HttpRequest;

import java.util.function.Function;

public final class PathRouteDefinition<S> {

    public final HttpRequest.HttpMethod httpMethod;
    public final String pathPattern;
    public final Function<HttpRequest, S> matchFun;

    public PathRouteDefinition(HttpRequest.HttpMethod httpMethod, String pathPattern, Function<HttpRequest, S> matchFun) {
        this.httpMethod = httpMethod;
        this.pathPattern = pathPattern;
        this.matchFun = matchFun;
    }
}
