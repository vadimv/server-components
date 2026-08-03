package rsp.component;

/**
 * Resolves a view function given an intent dispatcher.
 * A view can describe user interactions by dispatching intents, but it cannot
 * update the component's local state cache directly.
 *
 * @see IntentDispatcher
 * @see View
 * @see rsp.dsl.Definition
 * @param <S> a type of the state
 * @param <I> a type of intents accepted by the owning component
 */
@FunctionalInterface
public interface ComponentView<S, I> {

    /**
     * Resolves a view function.
     * @param intents dispatcher for the component's typed intents, must not be null
     * @return a function that can be used for obtaining of a UI definition
     */
    View<S> use(IntentDispatcher<I> intents);
}
