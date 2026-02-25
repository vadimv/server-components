package rsp.compositions.agent;

/**
 * The decision from an {@link IntentGate} after evaluating an {@link AgentIntent}.
 * <p>
 * Sealed to ensure exhaustive handling:
 * <ul>
 *   <li>{@link Allow} — intent is permitted, proceed with event dispatch</li>
 *   <li>{@link Block} — intent is denied, reply with reason</li>
 *   <li>{@link Confirm} — intent requires user confirmation before proceeding</li>
 * </ul>
 */
public sealed interface GateResult {

    record Allow(AgentIntent intent) implements GateResult {}

    record Block(String reason) implements GateResult {}

    record Confirm(String question, AgentIntent intent) implements GateResult {}
}
