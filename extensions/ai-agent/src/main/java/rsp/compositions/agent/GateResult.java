package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;


import rsp.compositions.contract.ContractAction;

/**
 * The decision from an {@link ActionGate} after evaluating an {@link ContractAction}.
 * <p>
 * Sealed to ensure exhaustive handling:
 * <ul>
 *   <li>{@link Allow} — action is permitted, proceed with event dispatch</li>
 *   <li>{@link Block} — action is denied, reply with reason</li>
 *   <li>{@link Confirm} — action requires user confirmation before proceeding</li>
 * </ul>
 */
public sealed interface GateResult {

    record Allow(ContractAction action, ContractActionPayload payload) implements GateResult {}

    record Block(String reason) implements GateResult {}

    record Confirm(String question, ContractAction action, ContractActionPayload payload) implements GateResult {}
}
