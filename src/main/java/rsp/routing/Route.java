package rsp.routing;

import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a route.
 * In the case of an input match maps from the type T to a <code>CompletableFuture</code> wrapped in an <code>Optional</code>.
 * When there is no match returns an empty Optional.
 * @param <T> the input type
 * @param <S> the result type state type, should be an immutable class
 */
@FunctionalInterface
public interface Route<T, S> extends Function<T, Optional<S>> {
}
