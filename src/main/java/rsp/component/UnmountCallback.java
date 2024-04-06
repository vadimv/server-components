package rsp.component;

@FunctionalInterface
public interface UnmountCallback<S> {
    void apply(ComponentCompositeKey key, S state);
}
