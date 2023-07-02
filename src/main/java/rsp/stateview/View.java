package rsp.stateview;

import rsp.html.SegmentDefinition;

import java.util.function.Function;

/**
 * A function to create a state's view representation.
 * @param <S> the type of the document part's related state, should be an immutable class
 */
@FunctionalInterface

public interface View<S> extends Function<S, SegmentDefinition> {}
