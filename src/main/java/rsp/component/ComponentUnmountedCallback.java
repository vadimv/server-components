package rsp.component;

import rsp.page.PageObjects;

@FunctionalInterface
public interface ComponentUnmountedCallback<S> {

    void onComponentUnmounted(ComponentCompositeKey componentId, PageObjects.ComponentContext sessionPageObjects, S state);

}
