package rsp.component;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    S getState(ComponentCompositeKey componentKey, ComponentContext componentContext);

}
