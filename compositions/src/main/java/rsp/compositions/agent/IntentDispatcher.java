package rsp.compositions.agent;

import rsp.component.EventKey;
import rsp.component.Lookup;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ViewContract;

/**
 * The only component with publish access — translates allowed intents into framework events.
 * <p>
 * The dispatcher is <b>generic</b>: it looks up the action name in the contract's
 * {@link ViewContract#agentActions()} and publishes the associated {@link EventKey}.
 * No hardcoded switch for contract-specific events — the contract is the single source of truth.
 * <p>
 * Adding a new action to a contract automatically makes it available to the agent;
 * no dispatcher changes needed.
 */
public class IntentDispatcher {

    /**
     * Result of a dispatch attempt.
     */
    public sealed interface DispatchResult {
        record Dispatched(AgentIntent intent) implements DispatchResult {}
        record UnknownAction(String action) implements DispatchResult {}
        record Blocked(String reason) implements DispatchResult {}
        record AwaitingConfirmation(String question, AgentIntent intent) implements DispatchResult {}
    }

    /**
     * Evaluate intent through the gate, then dispatch if allowed.
     *
     * @param intent   the agent's intent
     * @param contract the active contract (used to look up declared actions)
     * @param lookup   the current context (for event publishing and gate evaluation)
     * @param gate     the rule engine
     * @return the dispatch result
     */
    public DispatchResult dispatch(AgentIntent intent, ViewContract contract,
                                   Lookup lookup, IntentGate gate) {
        GateResult result = gate.evaluate(intent, lookup);
        return switch (result) {
            case GateResult.Allow a -> publishEvent(a.intent(), contract, lookup);
            case GateResult.Block b -> new DispatchResult.Blocked(b.reason());
            case GateResult.Confirm c -> new DispatchResult.AwaitingConfirmation(c.question(), c.intent());
        };
    }

    /**
     * Dispatch an intent directly (no gate evaluation).
     * Used after confirmation has been received.
     */
    public DispatchResult dispatchDirect(AgentIntent intent, ViewContract contract, Lookup lookup) {
        return publishEvent(intent, contract, lookup);
    }

    @SuppressWarnings("unchecked")
    private DispatchResult publishEvent(AgentIntent intent, ViewContract contract, Lookup lookup) {
        // Navigation is contract-independent
        if ("navigate".equals(intent.action())) {
            lookup.publish(EventKeys.SET_PRIMARY, intent.targetContract());
            return new DispatchResult.Dispatched(intent);
        }

        // All other actions: look up in the contract's declared actions
        return contract.agentActions().stream()
            .filter(a -> a.action().equals(intent.action()))
            .findFirst()
            .<DispatchResult>map(action -> {
                EventKey<?> key = action.eventKey();
                if (key instanceof EventKey.VoidKey vk) {
                    lookup.publish(vk);
                } else if (key instanceof EventKey.SimpleKey sk) {
                    lookup.publish(sk, intent.params().get("payload"));
                }
                return new DispatchResult.Dispatched(intent);
            })
            .orElse(new DispatchResult.UnknownAction(intent.action()));
    }
}
