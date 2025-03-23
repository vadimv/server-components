package rsp.component;

@FunctionalInterface
public interface ComponentUnmountedCallback<S> {

    void onComponentUnmounted(ComponentCompositeKey key, S state);

}
