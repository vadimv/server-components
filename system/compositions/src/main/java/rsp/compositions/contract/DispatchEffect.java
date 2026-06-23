package rsp.compositions.contract;

/**
 * Declarative effect that dispatching a {@link ContractAction} has on the
 * scene's routed contract. Read by the agent runtime to decide whether to
 * await scene settlement between plan steps.
 * <p>
 * Mark the effect at the action declaration site (e.g. {@code create},
 * {@code edit}) so the runtime can gate the next step on the rebuild
 * completing rather than guessing with a timeout.
 */
public enum DispatchEffect {

    /**
     * Dispatch does not change the routed contract. No wait between steps.
     * Examples: {@code page}, {@code select_all}, {@code set_field}, {@code delete}
     * on a list (data-only refresh — same routed contract).
     */
    NONE,

    /**
     * Dispatch will trigger a scene rebuild — typically opens or closes a
     * contract, changing the routed contract class. The runtime arms a
     * {@code sceneSettleFuture} before dispatch and waits for it to complete
     * (with a long timeout) before the next plan iteration.
     * Examples: {@code create}, {@code edit} (open a form), {@code save},
     * {@code cancel} (close a form), {@code delete} on edit view (closes).
     */
    SCENE_CHANGE
}
