package rsp.component;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void onComponentUpdated(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate);

}
