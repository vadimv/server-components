package rsp.component;

@FunctionalInterface
public interface BeforeRenderCallback<S> {

    void apply(ComponentCompositeKey key, S state, NewState<S> newState, ComponentRenderContext beforeRenderCallback);

}
