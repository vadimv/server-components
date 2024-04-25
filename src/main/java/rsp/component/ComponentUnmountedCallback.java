package rsp.component;

@FunctionalInterface
public interface ComponentUnmountedCallback<S> {

    void apply(ComponentCompositeKey key, S state);

}
