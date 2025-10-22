package rsp.component;

import rsp.page.Lookup;

@FunctionalInterface
public interface ComponentUnmountedCallback<S> {

    void onComponentUnmounted(ComponentCompositeKey componentId, Lookup lookup, S state);

}
