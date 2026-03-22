package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.authorization.DelegationGrant;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link AgentSpawner} that delegates spawn decisions to an {@link Authorization}.
 * <p>
 * Assembles {@code action.*} and {@code control.*} attributes from the spawn request,
 * evaluates against the pre-bound authorization context, and mints a
 * delegation grant on Allow.
 */
public final class PolicySpawner implements AgentSpawner {
    private final Authorization authorization;
    private final Duration defaultTtl;

    public PolicySpawner(Authorization authorization, Duration defaultTtl) {
        this.authorization = Objects.requireNonNull(authorization);
        this.defaultTtl = defaultTtl;
    }

    public PolicySpawner(Authorization authorization) {
        this(authorization, null);
    }

    @Override
    public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
        Attributes actionAttrs = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "agent:spawn")
            .put(AttributeKeys.ACTION_TYPE, "execute")
            .put(AttributeKeys.CONTROL_MODE, request.controlMode().name().toLowerCase())
            .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
            .put(AttributeKeys.CONTEXT_TIME, Instant.now())
            .build();

        AccessDecision decision = authorization.evaluate(actionAttrs);
        return switch (decision) {
            case AccessDecision.Allow _ -> {
                Instant now = Instant.now();
                Instant expiresAt = defaultTtl != null ? now.plus(defaultTtl) : null;
                Attributes entitlements = Attributes.builder()
                    .put(AttributeKeys.CONTROL_MODE, request.controlMode().name().toLowerCase())
                    .build();
                DelegationGrant grant = new DelegationGrant(
                    UUID.randomUUID().toString(), entitlements, now, expiresAt);
                yield new SpawnResult.Approved(new AgentSession(UUID.randomUUID().toString(), grant));
            }
            case AccessDecision.Deny d -> new SpawnResult.Denied(d.reason());
        };
    }
}
