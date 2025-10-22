package rsp.component;

import rsp.page.Lookup;

@FunctionalInterface
public interface ComponentMountedCallback<S> {

    /**
     * Invoked during mount of the component. This callback can be used for modification of this component setup.
     *
     * @param componentId component's composite key
     * @param lookup
     * @param state       current state
     * @param stateUpdate update state target, must not be called directly, to be called asynchronous only
     */
    void onComponentMounted(ComponentCompositeKey componentId, Lookup lookup, S state, StateUpdate<S> stateUpdate);

}
