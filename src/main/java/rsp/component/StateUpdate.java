package rsp.component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Represents an API containing variants of initiating a change of a component's state.
 * One of the methods can be used depending on the requirement and convenience in a concrete use case.
 * @param <S> the type of the component's state to be updated
 */
public interface StateUpdate<S> {

    /**
     * Initiates an update of a state providing a new state.
     * @param newState a new state object
     */
    void setState(S newState);

    /**
     * Initiates an update of a state by providing a transformation function which is applied to the current state.
     * @param stateTransformer a function from an old to a new state
     */
    void applyStateTransformation(UnaryOperator<S> stateTransformer);

    /**
     * Initiates an update of a state by providing a transformation function which is conditionally applied to the current state.
     * @param stateTransformer a function from old state to an Optional of a new state, in case when the result is empty it is not used for an update
     */
    void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer);
}
