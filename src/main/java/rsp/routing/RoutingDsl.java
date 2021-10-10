package rsp.routing;

import rsp.server.HttpRequest;
import rsp.server.Path;
import rsp.util.TriFunction;
import rsp.util.data.Tuple2;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Provides HTTP request routing DSL functions.
 * DSL functions define routes for an incoming request on the base of an HTTP method and an URL's path.
 * The framework tries to match the HTTP request's method and path match and in case of success calls the matching function
 * to obtain a CompletableFuture with a global state object.
 *
 * @see Route
 */
public class RoutingDsl {

    /**
     * Concatenates routes.
     * @param routeDefinitions routes definitions
     * @param <S> the type of the applications root component's state, should be an immutable class
     * @return the result route definition
     */
    @SafeVarargs
    public static <T, S> Route<T, S> concat(Route<T, S>... routeDefinitions) {
        return new Routes<>(routeDefinitions);
    }

    public static <S> Route<Path, S> path(String pathPattern, CompletableFuture<S> value) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(pp::match,
                                     new PathMatchFunction<>(pp, (p1, p2) -> value));
    }

    public static <S> Route<Path, S> path(String pathPattern, Function<String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(pp::match,
                                     new PathMatchFunction<>(pp, (p1, p2) -> matchFun.apply(p1)));
    }

    public static <S> Route<Path, S> path(String pathPattern, BiFunction<String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(pp::match,
                                     new PathMatchFunction<>(pp, matchFun));
    }


    public static <S> Route<HttpRequest, S> get(Function<HttpRequest, Route<Path, S>> subRoutes) {
        return request -> request.method.equals(HttpRequest.HttpMethod.GET) ? subRoutes.apply(request).apply(request.path) : Optional.empty();
    }

    public static <S> Route<HttpRequest, S> get(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(req)));
    }

    public static <S> Route<HttpRequest, S> get(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(p1, req)));
    }

    public static <S> Route<HttpRequest, S> get(String pathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, matchFun));
    }


    public static <S> Route<HttpRequest, S> post(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(req)));
    }

    public static <S> Route<HttpRequest, S> post(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(p1, req)));
    }

    public static <S> Route<HttpRequest, S> post(String pathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, matchFun));
    }

    public static <S> Route<HttpRequest, S> put(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(req)));
    }

    public static <S> Route<HttpRequest, S> put(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, (p1, p2, req) -> matchFun.apply(p1, req)));
    }

    public static <S> Route<HttpRequest, S> put(String pathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return new RouteDefinition<>(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                                     new HttpRequestMatchFunction<>(pp, matchFun));
    }


    public static <S> Route<HttpRequest, S> match(Predicate<HttpRequest> matchPredicate, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        return httpRequest -> matchPredicate.test(httpRequest) ? Optional.of(matchFun.apply(httpRequest)) : Optional.empty();
    }

    public static <S> Route<HttpRequest, S> any(final S value) {
        return request -> Optional.of(CompletableFuture.completedFuture(value));
    }
}
