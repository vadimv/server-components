package rsp.component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Represents API for initiating a change of a state's snapshot.
 * @param <S> the type of the component's state, should be an immutable class
 */
public interface NewState<S> {

    void set(S newState);
    void apply(Function<S, S> stateTransformer);
    void applyWhenComplete(CompletableFuture<? extends S> newState);
    void applyIfPresent(Function<S, Optional<S>> stateTransformer);
}
