package rsp.stateview;

import rsp.html.TagDefinition;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A function to create a state's view representation.
 * @param <S> the type of the document part's related state, should be an immutable class
 */
@FunctionalInterface
public interface CreateViewFunction<S> extends Function<S, Function<Consumer<S>, TagDefinition>> {}
