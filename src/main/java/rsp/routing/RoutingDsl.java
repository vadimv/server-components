package rsp.routing;

import rsp.server.HttpRequest;
import rsp.util.TriFunction;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

public class RoutingDsl {

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
