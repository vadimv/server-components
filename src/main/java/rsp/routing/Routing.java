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
public class Routing<S> implements Function<HttpRequest, Optional<CompletableFuture<? extends S>>> {
    public final Optional<S> defaultStateValue;
    public final RouteDefinition<S>[] routeDefinitions;

    @SafeVarargs
    public Routing(S defaultStateValue,
                   RouteDefinition<S>... routes) {
        this.defaultStateValue = Optional.of(defaultStateValue);
        this.routeDefinitions = routes;
    }

    @SafeVarargs
    public Routing(RouteDefinition<S>... routeDefinitions) {
        this.defaultStateValue = Optional.empty();
        this.routeDefinitions = routeDefinitions;
    }

    @Override
    public Optional<CompletableFuture<? extends S>> apply(HttpRequest request) {
        for (RouteDefinition<S> routeDefinition : routeDefinitions) {
            if (routeDefinition.test(request)) {
                return Optional.of(routeDefinition.route().apply(request));
            }
        }
        return Optional.empty();
    }
}
