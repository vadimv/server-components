package rsp.component;

@FunctionalInterface
public interface MountCallback<S> {

    void apply(ComponentCompositeKey key, S state, NewState<S> newState, ComponentRenderContext componentRenderContext);

}
