package rsp.routing;

import rsp.server.HttpRequest;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Defines an application's HTTP requests routing.
 * @see RoutingDsl
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public class Routes<S> implements Function<HttpRequest, Optional<CompletableFuture<? extends S>>> {
    public final Optional<S> defaultStateValue;
    public final Function<HttpRequest, Optional<CompletableFuture<? extends S>>>[] routeDefinitions;

    @SafeVarargs
    private Routes(S defaultStateValue,
                   Function<HttpRequest, Optional<CompletableFuture<? extends S>>>... routes) {
        this.defaultStateValue = Optional.of(defaultStateValue);
        this.routeDefinitions = routes;
    }

    @SafeVarargs
    public Routes(Function<HttpRequest, Optional<CompletableFuture<? extends S>>>... routeDefinitions) {
        this.defaultStateValue = Optional.empty();
        this.routeDefinitions = routeDefinitions;
    }

    public Routes<S> notFound(S defaultStateValue) {
        return new Routes<>(defaultStateValue, routeDefinitions);
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(HttpRequest request) {
        for (Function<HttpRequest, Optional<CompletableFuture<? extends S>>> routeDefinition : routeDefinitions) {
            final Optional<CompletableFuture<? extends S>> result = routeDefinition.apply(request);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
