package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Routing<S> implements Function<HttpRequest, Optional<CompletableFuture<? extends S>>> {
    public final Optional<S> defaultStateValue;
    public final RouteDefinition<S>[] routeDefinitions;

    @SafeVarargs
    public Routing(S defaultStateValue,
                   RouteDefinition<S>... routes) {
        this.defaultStateValue = Optional.of(defaultStateValue);
        this.routeDefinitions = routes;
    }

    public Routing(RouteDefinition<S>... routeDefinitions) {
        this.defaultStateValue = Optional.empty();
        this.routeDefinitions = routeDefinitions;
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(HttpRequest request) {
        for (RouteDefinition<S> routeDefinition: routeDefinitions) {
            final Optional<CompletableFuture<? extends S>> route = routeDefinition.route().apply(request);
            if (route.isPresent()) {
                return route;
            }
        }
        return Optional.empty();
    }

    public static <S> RouteDefinition<S> get(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.GET, pathPattern, matchFun);
    }

    public static <S> RouteDefinition<S> get(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.GET, pathPattern, matchFun);
    }

    public static <S> RouteDefinition<S> get(String pathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.POST, pathPattern, matchFun);
    }

    public static <S> RouteDefinition<S> post(String pathPattern, Function<HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.POST, pathPattern, matchFun);
    }

    public static <S> RouteDefinition<S> post(String pathPattern, BiFunction<String, HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.POST, pathPattern, matchFun);
    }

    public static <S> RouteDefinition<S> post(String pathPattern, TriFunction<String, String, HttpRequest, CompletableFuture<S>> matchFun) {
        return new RouteDefinition<>(HttpRequest.HttpMethod.POST, pathPattern, matchFun);
    }

}
