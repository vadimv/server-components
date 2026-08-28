package rsp.compositions.agent;

import rsp.compositions.block.BlockActionPayload;


import rsp.compositions.block.BlockAction;

/**
 * The decision from an {@link ActionGate} after evaluating an {@link BlockAction}.
 * <p>
 * Sealed to ensure exhaustive handling:
 * <ul>
 *   <li>{@link Allow} — action is permitted, proceed with event dispatch</li>
 *   <li>{@link Block} — action is denied, reply with reason</li>
 *   <li>{@link Confirm} — action requires user confirmation before proceeding</li>
 * </ul>
 */
public sealed interface GateResult {

    record Allow(BlockAction action, BlockActionPayload payload) implements GateResult {}

    record Block(String reason) implements GateResult {}

    record Confirm(String question, BlockAction action, BlockActionPayload payload) implements GateResult {}
}
