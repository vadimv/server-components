package rsp.component;

/**
 * @param <S>
 */
@FunctionalInterface
public interface ComponentStateSupplier<S> {

    /**
     * @param componentKey
     * @param componentContext
     * @return
     */
    S getState(ComponentCompositeKey componentKey, ComponentContext componentContext);

}
