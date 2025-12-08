package rsp.component;

import rsp.dsl.Definition;

import java.util.function.Function;

/**
 * A function to create a state's view representation from a state.
 * @param <S> the type of the document part's related state
 */
@FunctionalInterface
public interface View<S> extends Function<S, Definition> {}
