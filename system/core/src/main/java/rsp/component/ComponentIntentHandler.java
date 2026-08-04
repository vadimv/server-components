package rsp.component;

/**
 * Handles intents dispatched from a component's view.
 *
 * @param <S> the component's immutable local state type
 * @param <I> the component's intent type
 */
@FunctionalInterface
public interface ComponentIntentHandler<S, I> {

    /**
     * Handles an intent with the component's current state and its private
     * state-cache update capability.
     * <p>
     * The framework invokes this method from the owning page's event loop after
     * the intent has been queued by {@link IntentDispatcher#dispatch(Object)}.
     *
     * @param intent the dispatched component intent
     * @param state the current immutable local state snapshot
     * @param stateUpdater capability for updating the local state cache
     */
    void onIntentDispatched(I intent, S state, StateUpdater<S> stateUpdater);

    /**
     * Creates a handler for low-level segments whose views never dispatch an
     * intent.
     *
     * @param <S> the component's immutable local state type
     * @param <I> the component's intent type
     * @return a handler that ignores every intent
     */
    static <S, I> ComponentIntentHandler<S, I> noOp() {
        return (intent, state, stateUpdater) -> { };
    }
}
