package rsp.routing;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Defines a routing.
 * @see RoutingDsl#concat
 * @param <T> the type of the component's input object, the 'state origin type'
 * @param <S> the type of the component's state, should be an immutable class
 */
public final class Routing<T, S> {
    private final S notFoundState;
    private final Route<T, S> routes;

    public Routing(final Route<T, S> routes, final S notFoundState) {
        this.routes = Objects.requireNonNull(routes);
        this.notFoundState = notFoundState;
    }

    public Routing(final Route<T, S> routes) {
        this(routes, null);
    }

    public CompletableFuture<? extends S> route(final T request) {
        final var result = routes.apply(request);
        if (notFoundState != null) {
            return result.orElse(CompletableFuture.completedFuture(notFoundState));
        } else {
            return result.orElseThrow(() -> new RuntimeException("Not found 404"));
        }
    }

    public Function<T, CompletableFuture<? extends S>> toInitialStateFunction() {
        return httpRequest -> route(httpRequest);
    }

}
