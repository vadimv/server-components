package rsp.component;

import java.util.function.Function;

/**
 * A function that gets a state update API as its parameter and returns a stateful component's view.
 * @param <S> the type of the document part's related state
 */
@FunctionalInterface
public interface ComponentView<S> extends Function<StateUpdate<S>, View<S>> {}
