package rsp.routing;

import rsp.server.Path;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public final class PathRouteDefinition<S> extends RouteDefinition<Path, S> {
    public PathRouteDefinition(Predicate<Path> predicate, Function<Path, CompletableFuture<S>> matchFun) {
        super(predicate, matchFun);
    }
}
