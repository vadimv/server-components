package rsp.compositions.agent;

import rsp.compositions.contract.AgentPayload;


import rsp.compositions.contract.AgentAction;

/**
 * The decision from an {@link ActionGate} after evaluating an {@link AgentAction}.
 * <p>
 * Sealed to ensure exhaustive handling:
 * <ul>
 *   <li>{@link Allow} — action is permitted, proceed with event dispatch</li>
 *   <li>{@link Block} — action is denied, reply with reason</li>
 *   <li>{@link Confirm} — action requires user confirmation before proceeding</li>
 * </ul>
 */
public sealed interface GateResult {

    record Allow(AgentAction action, AgentPayload payload) implements GateResult {}

    record Block(String reason) implements GateResult {}

    record Confirm(String question, AgentAction action, AgentPayload payload) implements GateResult {}
}
