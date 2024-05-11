package rsp.routing;

import rsp.server.http.HttpRequest;
import rsp.server.Path;
import rsp.util.TriFunction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Contains routing DSL.
 * The DSL functions define routes for an incoming request on the base of an HTTP method and a URL's componentPath.
 * The framework tries to match the HTTP request's method and componentPath match and in case of success calls the matching function
 * to obtain a CompletableFuture with a global state object.
 * The class contains explicit routing for GET and POST HTTP verbs,
 * use the {@link RoutingDsl#match} for other verbs.
 *
 * @see Route
 */
public final class RoutingDsl {

    private RoutingDsl() {}

    /**
     * Creates a routing.
     * @param routes routes to try the input object
     * @param notFoundState the result when none of the provided routes matched
     * @param <T> the type of the input object, e.g. Path or HttpRequest
     * @param <S> the type of the applications root component's state, should be an immutable class
     * @return the result routing
     */
    public static <T, S> Routing<T, S> routing(final Route<T, S> routes, final S notFoundState) {
        return new Routing<>(routes, notFoundState);
    }

    /**
     * Concatenates routes.
     * @param routeDefinitions routes definitions
     * @param <S> the type of the applications root component's state, should be an immutable class
     * @return the result route definition
     */
    @SafeVarargs
    public static <T, S> Route<T, S> concat(final Route<T, S>... routeDefinitions) {
        return new ConcatRoutes<>(routeDefinitions);
    }

    /**
     * Creates a componentPath-specific route.
     * @param pathPattern the match componentPath pattern
     * @param value the result state
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(final String pathPattern, final CompletableFuture<S> value) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, (p1, p2) -> value));
    }

    /**
     * Creates a componentPath-specific route with one matching componentPath parameter.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a componentPath parameter as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(final String pathPattern, final Function<String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, (p1, p2) -> matchFun.apply(p1)));
    }

    /**
     * Creates a componentPath-specific route with two matching componentPath parameters.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a componentPath parameter as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<Path, S> path(final String pathPattern, final BiFunction<String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(pp::match,
                     new PathMatchFunction<>(pp, matchFun));
    }

    /**
     * Creates a route which delegates matching of GET requests to the provided componentPath matching sub-routes.
     * @param subRoutes the function from an HTTP request to sub routes.
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     */
    public static <S> Route<HttpRequest, S> get(final Function<HttpRequest, Route<Path, S>> subRoutes) {
        return req -> req.method.equals(HttpRequest.HttpMethod.GET) ? subRoutes.apply(req).apply(req.path) : Optional.empty();
    }

    /**
     * Creates a route which matches a GET request and the provided componentPath.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(final String pathPattern, final Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req)));
    }

    /**
     * Creates a route which matches a GET request and the provided componentPath with one componentPath parameter.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object and the first matched componentPath parameter its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(final String pathPattern, final BiFunction<HttpRequest, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req, p1)));
    }



    /**
     * Creates a route which matches a GET request and the provided componentPath with two componentPath parameters.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object, the first and second matched componentPath parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> get(final String pathPattern, final TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.GET.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, matchFun));
    }

    /**
     * Creates a route which matches a POST request and the provided componentPath.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object as its argument,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(final String pathPattern, final Function<HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(req)));
    }

    /**
     * Creates a route which matches a GET request and the provided componentPath with two componentPath parameters.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object, the first matched componentPath parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(final String pathPattern, final BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        final PathPattern pp = PathPattern.of(pathPattern);
        return match(req -> HttpRequest.HttpMethod.POST.equals(req.method) && pp.match(req.path),
                     new HttpRequestMatchFunction<>(pp, (req, p1, p2) -> matchFun.apply(p1, req)));
    }

    /**
     * Creates a route which matches a GET request and the provided componentPath with two componentPath parameters.
     * @param pathPattern the match componentPath pattern
     * @param matchFun the function taking a request object, the first and second matched componentPath parameters its arguments,
     *                 returning the result state as a CompletableFuture
     * @param <S> the type of the applications a component's state, should be an immutable class
     * @return the result route definition
     *
     * @see PathPattern#of(String)
     */
    public static <S> Route<HttpRequest, S> post(final String pathPattern, final TriFunction<HttpRequest, String, String, CompletableFuture<S>> matchFun) {
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
    public static <T, S> Route<T, S> match(final Predicate<T> matchPredicate, final Function<T, CompletableFuture<S>> matchFun) {
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


    public static <T, S> Function<T, CompletableFuture<? extends S>> whenRouteNotFound(Route<T, S>  routing, S notFoundState) {
        return t -> routing.apply(t).orElse(CompletableFuture.completedFuture(notFoundState));
    }

    private static final class ConcatRoutes<T, S> implements Route<T, S> {

        private final Route<T, S>[] routing;

        @SafeVarargs
        public ConcatRoutes(final Route<T, S>... routing) {
            this.routing = routing;
        }

        @Override
        public Optional<CompletableFuture<? extends S>> apply(final T request) {
            for (final Route<T, S> route : routing) {
                final Optional<CompletableFuture<? extends S>> result = route.apply(request);
                if (result.isPresent()) {
                    return result;
                }
            }
            return Optional.empty();
        }
    }
}
