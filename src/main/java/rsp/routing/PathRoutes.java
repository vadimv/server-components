package rsp.routing;

import rsp.server.HttpRequest;
import rsp.server.Path;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Defines an application's HTTP requests routing.
 * @see RoutingDsl
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public class PathRoutes<S> implements Route<Path, S> {
    public final Optional<S> defaultStateValue;
    public final Route<Path, S>[] routeDefinitions;

    @SafeVarargs
    private PathRoutes(S defaultStateValue,
                       Route<Path, S>... routes) {
        this.defaultStateValue = Optional.of(defaultStateValue);
        this.routeDefinitions = routes;
    }

    @SafeVarargs
    public PathRoutes(Route<Path, S>... routeDefinitions) {
        this.defaultStateValue = Optional.empty();
        this.routeDefinitions = routeDefinitions;
    }

    public PathRoutes<S> notFound(S defaultStateValue) {
        return new PathRoutes<>(defaultStateValue, routeDefinitions);
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(Path request) {
        for (Function<Path, Optional<CompletableFuture<? extends S>>> routeDefinition : routeDefinitions) {
            final Optional<CompletableFuture<? extends S>> result = routeDefinition.apply(request);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
