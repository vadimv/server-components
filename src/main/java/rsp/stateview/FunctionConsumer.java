package rsp.stateview;

import java.util.Optional;
import java.util.function.Function;

/**
 * Represents a write state snapshot operation using the result of a function which transforms the current state to a new state.
 * The function execution and write may be performed with synchronization, making the state update atomic.
 * @param <S> the type of the state snapshot, an immutable class
 */
public interface FunctionConsumer<S> {
    /**
     * Writes the new state with the result of the function.
     * @param function the state transformation
     */
    void accept(Function<S, S> function);

    /**
     * Writes the new state with the result of the function if the result is not empty.
     * @param function the state transformation
     */
    void acceptOptional(Function<S, Optional<S>> function);
}
