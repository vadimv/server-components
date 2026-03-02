package rsp.compositions.agent;

import java.time.Instant;
import java.util.Collection;

/**
 * Grant constraint checks for ABAC policies.
 * <p>
 * Implements {@code withinGrant} logic: revocation, expiry, action scope,
 * and control mode scope validation.
 */
public final class GrantConstraints {
    private GrantConstraints() {}

    /**
     * Check if an operation is within the grant's constraints using attribute values.
     *
     * @param attributes the ABAC attributes (must include grant.* namespace)
     * @return {@code null} if within grant, or a reason string if not
     */
    @SuppressWarnings("unchecked")
    public static String check(Attributes attributes) {
        // No grant attributes = human user, no grant constraints apply
        if (!attributes.hasKey(AttributeKeys.GRANT_SCOPE_ACTIONS)
                && !attributes.hasKey(AttributeKeys.GRANT_SCOPE_CONTROL_MODES)
                && !attributes.hasKey(AttributeKeys.GRANT_REVOKED)
                && !attributes.hasKey(AttributeKeys.GRANT_EXPIRES_AT)) {
            return null;
        }

        // Revocation check
        Boolean revoked = attributes.getTyped(AttributeKeys.GRANT_REVOKED, Boolean.class);
        if (Boolean.TRUE.equals(revoked)) {
            return "Grant has been revoked";
        }

        // Expiry check
        Instant expiresAt = attributes.getTyped(AttributeKeys.GRANT_EXPIRES_AT, Instant.class);
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return "Grant expired";
        }

        // Action scope check
        String actionName = attributes.getString(AttributeKeys.ACTION_NAME);
        Collection<String> allowedActions = (Collection<String>) attributes.get(AttributeKeys.GRANT_SCOPE_ACTIONS);
        if (allowedActions != null && actionName != null && !allowedActions.contains(actionName)) {
            return "Action '" + actionName + "' not in grant scope";
        }

        // Control mode scope check
        String controlMode = attributes.getString(AttributeKeys.CONTROL_MODE);
        Collection<String> allowedModes = (Collection<String>) attributes.get(AttributeKeys.GRANT_SCOPE_CONTROL_MODES);
        if (allowedModes != null && controlMode != null && !allowedModes.contains(controlMode)) {
            return "Control mode '" + controlMode + "' not in grant scope";
        }

        return null;
    }
}
