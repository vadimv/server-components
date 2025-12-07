package rsp.component;

/**
 * Represents callbacks invoked during a component's life cycle within a components segments tree.
 * These callbacks are intended for use as application-level callbacks.
 * @param <S> a component's state type
 */
public interface ComponentSegmentLifeCycle<S> {

    /**
     * Invoked during mount of the component.
     * This callback can be used to subscribe to some external events.
     *
     * @param componentId component's composite key
     * @param state       current state
     * @param stateUpdate update state target, must not be called directly, to be called asynchronously; it is safe to call in a different tread
     */
    void onComponentMounted(ComponentCompositeKey componentId, S state, StateUpdate<S> stateUpdate);

    void onComponentUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate);

    void onComponentUnmounted(ComponentCompositeKey componentId, S state);
}
