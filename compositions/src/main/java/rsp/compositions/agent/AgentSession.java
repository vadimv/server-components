package rsp.compositions.agent;

import java.util.Objects;

/**
 * Handle for an active agent session, returned on successful spawn.
 * <p>
 * The session is valid as long as its delegation grant has not expired.
 * Revocation support can be added via a revocation store keyed by {@code grant.grantId()}.
 *
 * @param sessionId unique session identifier
 * @param grant     the delegation grant governing this session
 */
public record AgentSession(
    String sessionId,
    DelegationGrant grant
) {
    public AgentSession {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(grant);
    }

    public boolean isValid() {
        return !grant.isExpired();
    }
}
