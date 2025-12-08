package rsp.component;

import java.util.function.Function;

/**
 * A function getting a state update API as its parameter and returning a stateful component's view.
 * @param <S> the type of the document part's related state, should be an immutable class
 */
@FunctionalInterface
public interface ComponentView<S> extends Function<StateUpdate<S>, View<S>> {}
