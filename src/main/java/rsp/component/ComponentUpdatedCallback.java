package rsp.component;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void apply(ComponentCompositeKey key, S oldState, S state, StateUpdate<S> stateUpdate);

}
