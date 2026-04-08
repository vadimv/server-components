package rsp.compositions.authorization;

import java.util.Objects;
import java.util.Set;

/**
 * Pre-bound authorization context for ABAC policy evaluation.
 * <p>
 * Combines an {@link AccessPolicy} with subject attributes (who is acting),
 * so callers only need to supply action/resource attributes per evaluation.
 * <p>
 * Use {@link #delegated(DelegationGrant)} to derive a scoped authorization
 * for a deputy (agent, scheduler, service) acting under a delegation grant.
 * Use {@link #scoped(Set)} for contract-issued entitlements where
 * the contract itself is the authority.
 */
public final class Authorization {
    private final AccessPolicy policy;
    private final Attributes subjectAttributes;

    public Authorization(AccessPolicy policy, Attributes subjectAttributes) {
        this.policy = Objects.requireNonNull(policy);
        this.subjectAttributes = Objects.requireNonNull(subjectAttributes);
    }

    /**
     * Evaluate an action against this authorization context.
     * <p>
     * Merges the pre-bound subject attributes with the supplied
     * action/resource attributes, then evaluates the policy.
     */
    public AccessDecision evaluate(Attributes actionAttributes) {
        return policy.evaluate(subjectAttributes.merge(actionAttributes));
    }

    /**
     * Derive a delegated authorization for a deputy acting under a grant.
     * <p>
     * The grant's attributes (scope, expiry, etc.) are merged into the
     * subject context. The same policy applies.
     */
    public Authorization delegated(DelegationGrant grant) {
        Objects.requireNonNull(grant);
        return new Authorization(policy, subjectAttributes.merge(grant.toAttributes()));
    }

    /**
     * Derive a scoped authorization with specific action entitlements.
     * <p>
     * Used when a contract (trusted code) spawns a background task
     * and wants to limit what actions the task can perform.
     * No user approval needed — the contract is the authority.
     */
    public Authorization scoped(Set<String> entitlements) {
        Objects.requireNonNull(entitlements);
        return new Authorization(policy, subjectAttributes.extend(
            AttributeKeys.GRANT_SCOPE_ACTIONS, entitlements));
    }

    public AccessPolicy policy() {
        return policy;
    }

    public Attributes subjectAttributes() {
        return subjectAttributes;
    }
}
