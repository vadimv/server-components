package rsp.component;

@FunctionalInterface
public interface ComponentUnmountedCallback<S> {

    void onComponentUnmounted(ComponentCompositeKey componentId, S state);

}
