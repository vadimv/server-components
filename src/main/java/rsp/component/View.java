package rsp.component;

import rsp.dsl.Definition;

/**
 * Resolves a UI definition given a state.
 * @param <S> the type of the state
 */
@FunctionalInterface
public interface View<S> {

    /**
     * Resolves a UI definition.
     * @param state a state object
     * @return a definition ready render a UI tree
     */
    Definition apply(S state);
}
