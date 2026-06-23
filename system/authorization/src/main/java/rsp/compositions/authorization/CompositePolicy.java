package rsp.compositions.authorization;

import java.util.List;
import java.util.Objects;

/**
 * Composite policy that chains multiple policies.
 * <p>
 * First deny wins. If all policies allow, the result is Allow.
 */
public final class CompositePolicy implements AccessPolicy {
    private final List<AccessPolicy> policies;

    public CompositePolicy(List<AccessPolicy> policies) {
        Objects.requireNonNull(policies);
        this.policies = List.copyOf(policies);
    }

    public CompositePolicy(AccessPolicy... policies) {
        this(List.of(policies));
    }

    @Override
    public AccessDecision evaluate(Attributes attributes) {
        for (AccessPolicy policy : policies) {
            AccessDecision decision = policy.evaluate(attributes);
            if (decision instanceof AccessDecision.Deny) {
                return decision;
            }
        }
        return new AccessDecision.Allow();
    }
}
