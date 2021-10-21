package rsp.routing;

import rsp.server.HttpRequest;
import rsp.server.Path;
import rsp.util.TriFunction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Contains routing DSL.
 * The DSL functions define routes for an incoming request on the base of an HTTP method and a URL's path.
 * The framework tries to match the HTTP request's method and path match and in case of success calls the matching function
 * to obtain a CompletableFuture with a global state object.
 * The class contains explicit routing for GET and POST HTTP verbs,
 * use the {@link RoutingDsl#match} for other verbs.
 *
 * @see Route
 */
public final class RoutingDsl {

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

    /**
     * Creates a path-specific route.
     * @param pathPattern the match path pattern
     * @param value the result state
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(String pathPattern, CompletableFuture<S> value) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, (p1, p2) -> value));
    }

    /**
     * Creates a path-specific route with one matching path parameter.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a path parameter as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(String pathPattern, Function<String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, (p1, p2) -> matchFun.apply(p1)));
    }

    /**
     * Creates a path-specific route with two matching path parameters.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a path parameter as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(String pathPattern, BiFunction<String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, matchFun));
    }

    /**
     * Creates a route which delegates matching of GET requests to the provided path matching sub-routes.
     * @param subRoutes the function from a HTTP request to sub routes.
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     */
    public static <S> Route<HttpRequest, S> get(Function<HttpRequest, Route<Path, S>> subRoutes) {
        return req -> req.method.equals(HttpRequest.HttpMethod.GET) ? subRoutes.apply(req).apply(req.path) : Optional.empty();
    }

    /**
     * Creates a route which matches a GET request and the provided path.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req)));
    }

    /**
     * Creates a route which matches a GET request and the provided path with one path parameter.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object and the first matched path parameter its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(String pathPattern, BiFunction<HttpRequest, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req, p1)));
    }


    /**
     * Creates a route which matches a GET request and the provided path with two path parameters.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object, the first and second matched path parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(String pathPattern, TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, matchFun));
    }

    /**
     * Creates a route which matches a POST request and the provided path.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req)));
    }

    /**
     * Creates a route which matches a GET request and the provided path with two path parameters.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object, the first matched path parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(p1, req)));
    }

    /**
     * Creates a route which matches a GET request and the provided path with two path parameters.
     * @param pathPattern the match path pattern
     * @param matchFun the function taking a request object, the first and second matched path parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(String pathPattern, TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, matchFun));
    }

    /**
     *
     * @param matchPredicate determines if this route is a match
     * @param matchFun the function taking a request object,returning the result state as a CompletableFuture
     * @param <T> the input type
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return  he result route definition
     */
    public static <T, S> Route<T, S> match(Predicate<T> matchPredicate, Function<T, CompletableFuture<S>> matchFun) {
        return t -> matchPredicate.test(t) ? Optional.of(matchFun.apply(t)) : Optional.empty();
    }

    /**
     * Creates a route that matches to any request.
     * @param value the result's state value
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     */
    public static <S> Route<HttpRequest, S> any(final S value) {
        return req -> Optional.of(CompletableFuture.completedFuture(value));
    }
}
