package rsp.component;

@FunctionalInterface
public interface StateAppliedCallback<S> {
    void apply(ComponentCompositeKey key, S oldState, S state, NewState<S> newState, ComponentRenderContext beforeRenderCallback);
}
