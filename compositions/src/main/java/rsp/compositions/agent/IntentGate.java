package rsp.compositions.agent;

import rsp.component.Lookup;

/**
 * Rule engine interface between agent intents and event dispatch.
 * <p>
 * The agent never publishes events directly. It emits an {@link AgentIntent},
 * and the gate decides whether to allow, block, or request confirmation.
 * <p>
 * Implementations can check roles, enforce policies, or require confirmation
 * for destructive actions.
 */
public interface IntentGate {

    /**
     * Evaluate an intent against rules.
     *
     * @param intent the agent's intent
     * @param lookup the current context (for role checks, etc.)
     * @return the gate decision
     */
    GateResult evaluate(AgentIntent intent, Lookup lookup);
}
