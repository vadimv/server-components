package rsp.compositions.agent;

import java.util.Objects;

/**
 * Request to spawn an agent session.
 *
 * @param scope       the discovery scope level for this session
 * @param controlMode assist (user confirms) or autoplay (agent executes)
 * @param purpose     optional human-readable description of the session's purpose
 */
public record SpawnRequest(
    AgentContext.Scope scope,
    ControlMode controlMode,
    String purpose
) {
    public SpawnRequest {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(controlMode);
    }
}
