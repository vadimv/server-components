package rsp.component;

/**
 * Capability exposed to a view for requesting work from its owning component.
 * Views use intents instead of mutating component state directly.
 * <p>
 * Dispatch is asynchronous: intent handling is queued onto the owning page's
 * event loop, where it observes the component's latest state.
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
