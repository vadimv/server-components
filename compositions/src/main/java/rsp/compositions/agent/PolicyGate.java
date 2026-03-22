package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Authorization;

import java.time.Instant;
import java.util.Objects;

/**
 * {@link IntentGate} that delegates execution decisions to an {@link Authorization}.
 * <p>
 * Maps {@link AccessDecision.Allow} to {@link GateResult.Allow},
 * and {@link AccessDecision.Deny} to {@link GateResult.Block}.
 * <p>
 * Confirmation logic is not handled here — compose with a separate gate
 * if confirmation is needed.
 */
public final class PolicyGate implements IntentGate {
    private final Authorization authorization;

    public PolicyGate(Authorization authorization) {
        this.authorization = Objects.requireNonNull(authorization);
    }

    @Override
    public GateResult evaluate(AgentIntent intent, Lookup lookup) {
        Attributes.Builder b = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, intent.action())
            .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
            .put(AttributeKeys.CONTEXT_TIME, Instant.now());

        if (intent.targetContract() != null) {
            b.put(AttributeKeys.RESOURCE_CONTRACT_CLASS, intent.targetContract().getName());
            b.put(AttributeKeys.RESOURCE_KIND, "contract");
        }

        AccessDecision decision = authorization.evaluate(b.build());
        return switch (decision) {
            case AccessDecision.Allow _ -> new GateResult.Allow(intent);
            case AccessDecision.Deny d -> new GateResult.Block(d.reason());
        };
    }
}
