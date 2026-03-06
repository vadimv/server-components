package rsp.compositions.agent;

/**
 * Control attribute for agent interaction.
 * <p>
 * {@code ASSIST} — agent suggests actions, user confirms before execution.
 * {@code AUTOPLAY} — agent executes autonomously within grant scope.
 * <p>
 * Orthogonal to action type ({@code read/write/delete/execute}).
 * Both must be checked by ABAC policy.
 */
public enum ControlMode {
    ASSIST,
    AUTOPLAY
}
