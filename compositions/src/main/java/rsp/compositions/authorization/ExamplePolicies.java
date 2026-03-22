package rsp.compositions.authorization;

import java.util.Set;

/**
 * Reusable ABAC policy building blocks demonstrating the pattern.
 * <p>
 * Combine via {@link CompositePolicy} for production use.
 */
public final class ExamplePolicies {
    private ExamplePolicies() {}

    /**
     * Allows everything.
     */
    public static AccessPolicy allowAll() {
        return _ -> new AccessDecision.Allow();
    }

    /**
     * Denies everything.
     */
    public static AccessPolicy denyAll() {
        return _ -> new AccessDecision.Deny("Denied by policy");
    }

    /**
     * Requires the subject to have a user ID (i.e., be authenticated).
     */
    public static AccessPolicy requireAuthenticated() {
        return attrs -> attrs.hasKey(AttributeKeys.SUBJECT_USER_ID)
            ? new AccessDecision.Allow()
            : new AccessDecision.Deny("User not authenticated");
    }

    /**
     * Allows only read-type actions and safe navigation actions.
     */
    public static AccessPolicy readOnly() {
        Set<String> safeActions = Set.of(
            "navigate", "page", "select_all", "discover", "agent:spawn");
        return attrs -> {
            String actionType = attrs.getString(AttributeKeys.ACTION_TYPE);
            if ("read".equals(actionType) || "discover".equals(actionType)) {
                return new AccessDecision.Allow();
            }
            String actionName = attrs.getString(AttributeKeys.ACTION_NAME);
            if (actionName != null && safeActions.contains(actionName)) {
                return new AccessDecision.Allow();
            }
            return new AccessDecision.Deny(
                "Action '" + actionName + "' not allowed in read-only mode");
        };
    }

    /**
     * Validates delegation grant constraints (revocation, expiry, scope).
     * <p>
     * Passes through if no grant attributes are present (human user).
     */
    public static AccessPolicy grantConstraints() {
        return attrs -> {
            String violation = GrantConstraints.check(attrs);
            return violation == null
                ? new AccessDecision.Allow()
                : new AccessDecision.Deny(violation);
        };
    }
}
