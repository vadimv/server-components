package rsp.component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Represents API for initiating a change of a state's snapshot.
 * @param <S> the type of the component's state, should be an immutable class
 */
public interface NewState<S> {

    void set(S newState);
    void apply(UnaryOperator<S> stateTransformer);
    void applyWhenComplete(CompletableFuture<? extends S> newState);
    void applyIfPresent(Function<S, Optional<S>> stateTransformer);
}
