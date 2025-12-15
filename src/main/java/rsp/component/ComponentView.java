package rsp.component;

import java.util.function.Function;

/**
 * This interface provides an abstraction of a component's UI definitions tree
 * with support of the mechanism of initiating of this component's state changes.
 * This function gets a state update API as its parameter that can be used to initiate state updates.
 * @see StateUpdate
 * @see View
 * @see rsp.dsl.Definition
 * @param <S> a type of the state
 */
@FunctionalInterface
public interface ComponentView<S> extends Function<StateUpdate<S>, View<S>> {}
