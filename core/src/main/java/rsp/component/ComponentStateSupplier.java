package rsp.component;

/**
 * A function to resolve an initial state for a component.
 * @param <S> the type of the state object
 */
@FunctionalInterface
public interface ComponentStateSupplier<S> {
    /**
     * Resolves the initial state.
     * @param key the component's unique key
     * @param context the component's context
     * @return the initial state, must not be null
     * @throws IllegalStateException or NullPointerException if the resolved state is null // TODO
     */
    S getState(ComponentCompositeKey key, ComponentContext context);
}
