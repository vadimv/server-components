package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * {@link AgentActionFilter} that delegates discovery decisions to an {@link AccessPolicy}.
 * <p>
 * Evaluates each action independently. An action is visible only if the policy allows it.
 * Populates {@code action.*}, {@code grant.*}, and {@code subject.*} attributes per action.
 */
public final class PolicyActionFilter implements AgentActionFilter {
    private final AccessPolicy policy;
    private final DelegationGrant grant;

    public PolicyActionFilter(AccessPolicy policy, DelegationGrant grant) {
        this.policy = Objects.requireNonNull(policy);
        this.grant = grant;
    }

    @Override
    public List<AgentAction> filter(List<AgentAction> actions, Lookup context) {
        return actions.stream()
            .filter(action -> {
                Attributes attrs = buildAttributes(action, context);
                return policy.evaluate(attrs) instanceof AccessDecision.Allow;
            })
            .toList();
    }

    private Attributes buildAttributes(AgentAction action, Lookup lookup) {
        Attributes.Builder b = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, action.action())
            .put(AttributeKeys.ACTION_TYPE, "discover")
            .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
            .put(AttributeKeys.CONTEXT_TIME, Instant.now());

        if (lookup != null) {
            b.put(AttributeKeys.SUBJECT_TYPE, "agent");
            b.put(AttributeKeys.SUBJECT_USER_ID, lookup.get(ContextKeys.AUTH_USER));
            b.put(AttributeKeys.SUBJECT_ROLES, lookup.get(ContextKeys.AUTH_ROLES));
        }

        populateGrantAttributes(b);
        return b.build();
    }

    private void populateGrantAttributes(Attributes.Builder b) {
        if (grant == null) return;
        b.put(AttributeKeys.SUBJECT_DELEGATION_GRANT_ID, grant.grantId());
        b.put(AttributeKeys.GRANT_EXPIRES_AT, grant.expiresAt());
        b.put(AttributeKeys.GRANT_REVOKED, false);
    }
}
