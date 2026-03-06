package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;

import java.time.Instant;
import java.util.Objects;

/**
 * {@link IntentGate} that delegates execution decisions to an {@link AccessPolicy}.
 * <p>
 * Maps {@link AccessDecision.Allow} to {@link GateResult.Allow},
 * and {@link AccessDecision.Deny} to {@link GateResult.Block}.
 * <p>
 * Confirmation logic is not handled here — compose with a separate gate
 * if confirmation is needed.
 */
public final class PolicyGate implements IntentGate {
    private final AccessPolicy policy;
    private final DelegationGrant grant;

    public PolicyGate(AccessPolicy policy, DelegationGrant grant) {
        this.policy = Objects.requireNonNull(policy);
        this.grant = grant;
    }

    @Override
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        Attributes attrs = buildAttributes(intent, lookup);
        AccessDecision decision = policy.evaluate(attrs);
        return switch (decision) {
            case AccessDecision.Allow _ -> new GateResult.Allow(intent);
            case AccessDecision.Deny d -> new GateResult.Block(d.reason());
        };
    }

    private Attributes buildAttributes(AgentIntent intent, Lookup lookup) {
        Attributes.Builder b = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, intent.action())
            .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
            .put(AttributeKeys.CONTEXT_TIME, Instant.now());

        if (intent.targetContract() != null) {
            b.put(AttributeKeys.RESOURCE_CONTRACT_CLASS, intent.targetContract().getName());
            b.put(AttributeKeys.RESOURCE_KIND, "contract");
        }

        if (lookup != null) {
            b.put(AttributeKeys.SUBJECT_TYPE, "agent");
            b.put(AttributeKeys.SUBJECT_USER_ID, lookup.get(ContextKeys.AUTH_USER));
            b.put(AttributeKeys.SUBJECT_ROLES, lookup.get(ContextKeys.AUTH_ROLES));
        }

        if (grant != null) {
            b.put(AttributeKeys.SUBJECT_DELEGATION_GRANT_ID, grant.grantId());
            b.put(AttributeKeys.GRANT_EXPIRES_AT, grant.expiresAt());
            b.put(AttributeKeys.GRANT_REVOKED, false);
        }

        return b.build();
    }
}
