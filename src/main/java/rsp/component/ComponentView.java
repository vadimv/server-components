package rsp.component;

/**
 * Resolves a view function given a state update object.
 * This functional interface injects a state update object enabling the framework to listen to the state updates
 * initiated by the client code.
 *
 * @see StateUpdate
 * @see View
 * @see rsp.dsl.Definition
 * @param <S> a type of the state
 */
@FunctionalInterface
public interface ComponentView<S> {

    /**
     * Resolves a view function.
     * @param stateUpdate a state updates listener
     * @return a function that can be used for obtaining of a UI definition
     */
    View<S> use(StateUpdate<S> stateUpdate);
}
