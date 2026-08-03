package rsp.component;

/**
 * Capability exposed to a view for requesting work from its owning component.
 * Views use intents instead of mutating component state directly.
 *
 * @param <I> the component's intent type
 */
@FunctionalInterface
public interface IntentDispatcher<I> {

    /**
     * Dispatch an intent to the owning component.
     *
     * @param intent a non-null component intent
     */
    void dispatch(I intent);
}
