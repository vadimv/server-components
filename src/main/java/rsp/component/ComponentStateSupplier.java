package rsp.component;

/**
 * This functional interface represents a source of a state snapshot of a component. A state snapshot to be used during rendering of a component.
 * @see ComponentSegment<S>
 * @param <S> a component's state type
 */
@FunctionalInterface
public interface ComponentStateSupplier<S> {

    /**
     * Provides a snapshot of a component's state, for example from a cache or retrieving from the component's context.
     * @param componentKey an identifier of this component to access data from cache
     * @param componentContext an instance of component's context from upstream components chain
     * @return a result state
     */
    S getState(ComponentCompositeKey componentKey, ComponentContext componentContext);

}
