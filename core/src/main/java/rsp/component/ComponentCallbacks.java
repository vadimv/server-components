package rsp.component;

/**
 * Defines callbacks that {@link ComponentSegment} invokes at various points during
 * a component's lifecycle and rendering process.
 * <p>
 * This interface separates the callback/hook mechanism from the component definition,
 * allowing for cleaner decoupling between the runtime segment and the component definition.
 * <p>
 * The callbacks include:
 * <ul>
 *     <li><b>Veto hooks:</b> {@link #onBeforeUpdated} can prevent state updates</li>
 *     <li><b>Subscription points:</b> {@link #onAfterRendered} for registering event handlers</li>
 *     <li><b>Lifecycle notifications:</b> {@link #onMounted}, {@link #onUpdated}, {@link #onUnmounted}</li>
 * </ul>
 *
 * @param <S> the component's state type
 * @see ComponentSegment
 */
public interface ComponentCallbacks<S> {

    /**
     * Called before a state update is applied.
     * Override this method to intercept state changes, e.g., to notify parent components
     * or to veto the update (by returning false).
     *
     * @param newState the proposed new state
     * @param commandsEnqueue for sending commands (e.g., ComponentEventNotification)
     * @return true to proceed with the update, false to veto it
     */
    boolean onBeforeUpdated(S newState, CommandsEnqueue commandsEnqueue);

    /**
     * Called after each render (both initial and re-renders).
     * Use this callback to subscribe to window or component events.
     *
     * @param state the current state
     * @param subscriber for adding event handlers (window, component events)
     * @param commandsEnqueue for sending commands (e.g., PushHistory)
     * @param stateUpdate for updating state from event handlers
     */
    void onAfterRendered(S state,
                         Subscriber subscriber,
                         CommandsEnqueue commandsEnqueue,
                         StateUpdate<S> stateUpdate);

    /**
     * Called after the component is initially mounted to the segments tree.
     * It is thread-safe to call the state update's methods in this callback.
     * <p>
     * Components that need access to the live mounted segment should override
     * {@link #onMounted(ComponentSegment, ComponentCompositeKey, Object, CommandsEnqueue, StateUpdate)}
     * instead. This callback is retained as the simple lifecycle hook.
     *
     * @param componentId component's composite key
     * @param state current state
     * @param stateUpdate for updating state asynchronously
     */
    void onMounted(ComponentCompositeKey componentId, S state, StateUpdate<S> stateUpdate);

    /**
     * Called after the component is initially mounted to the segments tree, with
     * access to the live segment that actually won reconciliation.
     * <p>
     * Override this method for mount-owned resources that need the segment's live
     * {@link ContextScope}, such as context watchers or event subscriptions that
     * must not be rebound from reconciliation candidates. The default delegates
     * to {@link #onMounted(ComponentCompositeKey, Object, StateUpdate)}.
     *
     * @param segment live mounted segment
     * @param componentId component's composite key
     * @param state current state
     * @param commandsEnqueue for sending commands
     * @param stateUpdate for updating state asynchronously
     */
    default void onMounted(ComponentSegment<S> segment,
                           ComponentCompositeKey componentId,
                           S state,
                           CommandsEnqueue commandsEnqueue,
                           StateUpdate<S> stateUpdate) {
        onMounted(componentId, state, stateUpdate);
    }

    /**
     * Called after the component's state is updated.
     * It is thread-safe to call the state update's methods in this callback.
     *
     * @param componentId component's composite key
     * @param oldState the previous state
     * @param newState the new state
     * @param stateUpdate for updating state asynchronously
     */
    void onUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate);

    /**
     * Called when the component is unmounted from the rendered tree.
     *
     * @param componentId component's composite key
     * @param state the current state
     */
    void onUnmounted(ComponentCompositeKey componentId, S state);
}
