package rsp.compositions.authorization;

import java.time.Instant;
import java.util.Objects;

/**
 * Opaque delegation grant minted on spawn approval.
 * <p>
 * Represents a scoped entitlement envelope for a deputy (agent, scheduler, service, etc.).
 * The {@link #entitlements} carry the grant's scope and constraints as ABAC attributes.
 *
 * @param grantId      unique identifier (UUID/ULID)
 * @param entitlements grant scope and constraints as ABAC attributes
 * @param createdAt    grant creation timestamp
 * @param expiresAt    expiry timestamp, or {@code null} for no expiry
 */
public record DelegationGrant(
    String grantId,
    Attributes entitlements,
    Instant createdAt,
    Instant expiresAt
) {
    public DelegationGrant {
        Objects.requireNonNull(grantId);
        Objects.requireNonNull(entitlements);
        Objects.requireNonNull(createdAt);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Convert this grant to ABAC attributes for policy evaluation.
     * Includes the grant ID, entitlements, expiry, and revocation status.
     */
    public Attributes toAttributes() {
        return entitlements
            .extend(AttributeKeys.GRANT_EXPIRES_AT, expiresAt)
            .extend(AttributeKeys.GRANT_REVOKED, false);
    }
}
