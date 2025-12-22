package rsp.component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A component's state update interface.
 * An implementation of this interface is provided by the framework to a component's logic.
 * @param <S> a type of the component's state, should be an immutable class or a record
 */
public interface StateUpdate<S> {
    /**
     * Sets a new state.
     * @param newState a new state object, must not be null
     * @throws NullPointerException if the new state is null
     */
    void setState(S newState);

    /**
     * Updates the current state by applying a state-to-state function.
     * @param stateTransformer a function for the state transformation, must not return null
     * @throws NullPointerException if the function returns null
     */
    void applyStateTransformation(UnaryOperator<S> stateTransformer);

    /**
     * Updates the current state by applying a state-to-optional-state function.
     * If the function returns an empty optional, the state is not updated.
     * @param stateTransformer a function for the state transformation
     */
    void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer);
}
