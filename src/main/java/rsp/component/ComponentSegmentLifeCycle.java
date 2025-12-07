package rsp.component;

public interface ComponentSegmentLifeCycle<S> {
    /**
     * Invoked during mount of the component. This callback can be used for modification of this component setup.
     *
     * @param componentId component's composite key
     * @param state       current state
     * @param stateUpdate update state target, must not be called directly, to be called asynchronous only
     */
    void onComponentMounted(ComponentCompositeKey componentId, S state, StateUpdate<S> stateUpdate);

    void onComponentUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate);

    void onComponentUnmounted(ComponentCompositeKey componentId, S state);
}
