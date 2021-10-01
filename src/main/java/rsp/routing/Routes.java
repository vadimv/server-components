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
public class Routes<S> implements Route<HttpRequest, S> {
    public final Function<HttpRequest, Optional<CompletableFuture<? extends S>>>[] routeDefinitions;

    @SafeVarargs
    public Routes(Route<HttpRequest, S>... routes) {
        this.routeDefinitions = routes;
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
