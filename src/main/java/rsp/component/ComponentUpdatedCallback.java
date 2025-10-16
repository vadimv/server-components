package rsp.component;

import rsp.page.PageObjects;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void onComponentUpdated(ComponentCompositeKey componentId, PageObjects.ComponentContext sessionPageObjects, S oldState, S newState, StateUpdate<S> stateUpdate);

}
