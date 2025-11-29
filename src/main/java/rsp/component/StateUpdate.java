package rsp.component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Represents an API for initiating a change of a component's state.
 * @param <S> the type of the component's state, should be an immutable class
 */
public interface StateUpdate<S> {

    void setState(S newState);

    void setStateWhenComplete(S newState);

    void applyStateTransformation(UnaryOperator<S> stateTransformer);

    void applyStateTransformationIfPresent(Function<S, Optional<S>> stateTransformer);
}
