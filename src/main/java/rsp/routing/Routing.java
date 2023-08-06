package rsp.routing;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Defines a routing.
 * @see RoutingDsl#concat
 * @param <T> the type of the component's input object, the 'state origin type'
 * @param <S> the type of the component's state, should be an immutable class
 */
public final class Routing<T, S> implements Function<T, CompletableFuture<? extends S>> {
    private final CompletableFuture<S> notFoundState;
    private final Route<T, S> routes;

    public Routing(final Route<T, S> routes, final CompletableFuture<S> notFoundState) {
        this.routes = Objects.requireNonNull(routes);
        this.notFoundState = Objects.requireNonNull(notFoundState);
    }

    public Routing(final Route<T, S> routes, final S notFoundState) {
        this.routes = Objects.requireNonNull(routes);
        this.notFoundState = CompletableFuture.completedFuture(Objects.requireNonNull(notFoundState));
    }

    @Override
    public CompletableFuture<? extends S> apply(final T stateOrigin) {
        final Optional<CompletableFuture<? extends S>> result = routes.apply(stateOrigin);
        return result.orElse(notFoundState);
    }
}
