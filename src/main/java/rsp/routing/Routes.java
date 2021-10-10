package rsp.routing;

import rsp.server.HttpRequest;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Defines an application's HTTP requests routing.
 * @see RoutingDsl
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public class Routes<T, S> implements Route<T, S> {
    public final Route<T, S>[] routeDefinitions;

    @SafeVarargs
    public Routes(Route<T, S>... routes) {
        this.routeDefinitions = routes;
    }


    @Override
    public Optional<CompletableFuture<? extends S>> apply(T request) {
        for (Route<T, S> routeDefinition : routeDefinitions) {
            final Optional<CompletableFuture<? extends S>> result = routeDefinition.apply(request);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }
}
