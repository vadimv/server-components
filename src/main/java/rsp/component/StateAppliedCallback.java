package rsp.component;

@FunctionalInterface
public interface StateAppliedCallback<S> {
    void apply(ComponentCompositeKey key, S state, ComponentRenderContext beforeRenderCallback);
}
