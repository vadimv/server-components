package rsp.compositions.agent;

import rsp.compositions.contract.AgentPayload;


import rsp.compositions.contract.AgentAction;

import rsp.component.Lookup;

/**
 * Rule engine interface between agent actions and event dispatch.
 * <p>
 * The agent never publishes events directly. It selects an {@link AgentAction},
 * and the gate decides whether to allow, block, or request confirmation.
 * <p>
 * Implementations can check roles, enforce policies, or require confirmation
 * for destructive actions.
 */
public interface ActionGate {

    /**
     * Evaluate an action against rules.
     *
     * @param action  the agent action to evaluate
     * @param payload the agent payload
     * @param lookup  the current context (for role checks, etc.)
     * @return the gate decision
     */
    GateResult evaluate(AgentAction action, AgentPayload payload, Lookup lookup);
}
