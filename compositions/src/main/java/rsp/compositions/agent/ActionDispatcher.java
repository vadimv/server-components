package rsp.compositions.agent;

import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ViewContract;

/**
 * The only component with publish access — translates allowed actions into framework events.
 * <p>
 * The dispatcher receives an {@link AgentAction} directly (no lookup by name needed)
 * and publishes the associated {@link EventKey} on the contract's lookup.
 * <p>
 * Navigation is handled separately via {@link #dispatchNavigate}.
 */
public class ActionDispatcher {

    /**
     * Result of a dispatch attempt.
     */
    public sealed interface DispatchResult {
        record Dispatched(AgentAction action, Object payload) implements DispatchResult {}
        record Blocked(String reason) implements DispatchResult {}
        record AwaitingConfirmation(String question, AgentAction action, Object rawPayload) implements DispatchResult {}
        record PayloadError(String action, String message) implements DispatchResult {}
    }

    /**
     * Evaluate action through the gate, then dispatch if allowed.
     *
     * @param action     the agent action to dispatch
     * @param rawPayload the raw payload (nullable)
     * @param contract   the active contract
     * @param lookup     the current context (for gate evaluation)
     * @param gate       the rule engine
     * @return the dispatch result
     */
    public DispatchResult dispatch(AgentAction action, Object rawPayload,
                                   ViewContract contract, Lookup lookup, ActionGate gate) {
        GateResult result = gate.evaluate(action, rawPayload, lookup);
        return switch (result) {
            case GateResult.Allow a -> publishEvent(a.action(), a.rawPayload(), contract);
            case GateResult.Block b -> new DispatchResult.Blocked(b.reason());
            case GateResult.Confirm c -> new DispatchResult.AwaitingConfirmation(c.question(), c.action(), c.rawPayload());
        };
    }

    /**
     * Dispatch an action directly (no gate evaluation).
     * Used after confirmation has been received.
     */
    public DispatchResult dispatchDirect(AgentAction action, Object rawPayload, ViewContract contract) {
        return publishEvent(action, rawPayload, contract);
    }

    /**
     * Dispatch a navigation event to switch the active contract.
     *
     * @param targetContract the contract class to navigate to
     * @param lookup         the current context (for event publishing)
     */
    public void dispatchNavigate(Class<? extends ViewContract> targetContract, Lookup lookup) {
        lookup.publish(EventKeys.SET_PRIMARY, targetContract);
    }

    @SuppressWarnings("unchecked")
    private DispatchResult publishEvent(AgentAction action, Object rawPayload, ViewContract contract) {
        Lookup contractLookup = contract.lookup();
        EventKey<?> key = action.eventKey();
        Object enrichedPayload = contract.enrichPayload(action, rawPayload);

        if (key instanceof EventKey.VoidKey vk) {
            contractLookup.publish(vk);
        } else if (key instanceof EventKey.SimpleKey<?> sk) {
            Object payload;
            try {
                payload = action.parsePayload().apply(enrichedPayload);
            } catch (IllegalArgumentException e) {
                return new DispatchResult.PayloadError(action.action(), e.getMessage());
            }
            contractLookup.publish((EventKey.SimpleKey) sk, payload);
        }
        return new DispatchResult.Dispatched(action, enrichedPayload);
    }
}
