package rsp.compositions.agent;

import java.time.Instant;
import java.util.Objects;

/**
 * Opaque delegation grant minted on spawn approval.
 * <p>
 * Represents the intersection of user entitlement and delegation constraints.
 *
 * @param grantId     unique identifier (UUID/ULID)
 * @param scope       discovery scope level
 * @param controlMode assist or autoplay
 * @param createdAt   grant creation timestamp
 * @param expiresAt   expiry timestamp, or {@code null} for no expiry
 */
public record DelegationGrant(
    String grantId,
    AgentContext.Scope scope,
    ControlMode controlMode,
    Instant createdAt,
    Instant expiresAt
) {
    public DelegationGrant {
        Objects.requireNonNull(grantId);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(controlMode);
        Objects.requireNonNull(createdAt);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
