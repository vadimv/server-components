package rsp.component;

import rsp.page.PageObjects;

@FunctionalInterface
public interface ComponentUpdatedCallback<S> {

    void onComponentUpdated(ComponentCompositeKey componentId, PageObjects.ComponentContext sessionPageObjects, S state, StateUpdate<S> stateUpdate);

}
