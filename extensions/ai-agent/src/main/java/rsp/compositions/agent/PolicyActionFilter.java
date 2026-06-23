package rsp.compositions.agent;

import rsp.compositions.contract.ContractAction;

import rsp.component.Lookup;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Authorization;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * {@link AgentActionFilter} that delegates discovery decisions to an {@link Authorization}.
 * <p>
 * Evaluates each action independently. An action is visible only if the authorization allows it.
 * The authorization already carries pre-bound subject and grant attributes.
 */
public final class PolicyActionFilter implements AgentActionFilter {
    private final Authorization authorization;

    public PolicyActionFilter(Authorization authorization) {
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override
    public List<ContractAction> filter(List<ContractAction> actions, Lookup context) {
        return actions.stream()
            .filter(action -> {
                Attributes attrs = Attributes.builder()
                    .put(AttributeKeys.ACTION_NAME, action.action())
                    .put(AttributeKeys.ACTION_TYPE, "discover")
                    .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
                    .put(AttributeKeys.CONTEXT_TIME, Instant.now())
                    .build();
                return authorization.evaluate(attrs) instanceof AccessDecision.Allow;
            })
            .toList();
    }
}
