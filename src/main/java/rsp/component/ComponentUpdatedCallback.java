package rsp.component;

import rsp.page.Lookup;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void onComponentUpdated(ComponentCompositeKey componentId, Lookup.ComponentContext sessionPageObjects, S oldState, S newState, StateUpdate<S> stateUpdate);

}
