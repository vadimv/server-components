package rsp.component;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void onComponentUpdated(ComponentCompositeKey componentId, S oldState, S newState, StateUpdate<S> stateUpdate);

}
