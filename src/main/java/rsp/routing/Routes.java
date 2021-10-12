package rsp.routing;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a sequence of routes.
 * @see RoutingDsl#concat
 * @param <S> the type of the applications root component's state, should be an immutable class
 */
public final class Routes<T, S> implements Route<T, S> {
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
