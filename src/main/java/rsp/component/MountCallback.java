package rsp.component;

@FunctionalInterface
public interface MountCallback<S> {

    /**
     * Invoked during mount of the component. This callback can be used for modification of this component setup.
     *
     * @param key component's composite key
     * @param state current state
     * @param stateUpdate update state target, must not be called directly, to be called asynchronous only
     * @param componentRenderContext
     */
    void apply(ComponentCompositeKey key, S state, StateUpdate<S> stateUpdate, ComponentRenderContext componentRenderContext);

}
