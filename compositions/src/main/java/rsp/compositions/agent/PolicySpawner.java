package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.page.QualifiedSessionId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link AgentSpawner} that delegates spawn decisions to an {@link AccessPolicy}.
 * <p>
 * Assembles {@code subject.*}, {@code action.*}, {@code control.*}, and {@code context.*}
 * attributes from the spawn request and lookup, evaluates the policy, and mints a
 * delegation grant on Allow.
 */
public final class PolicySpawner implements AgentSpawner {
    private final AccessPolicy policy;
    private final Duration defaultTtl;

    public PolicySpawner(AccessPolicy policy, Duration defaultTtl) {
        this.policy = Objects.requireNonNull(policy);
        this.defaultTtl = defaultTtl;
    }

    public PolicySpawner(AccessPolicy policy) {
        this(policy, null);
    }

    @Override
    public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
        Attributes attributes = buildAttributes(request, lookup);
        AccessDecision decision = policy.evaluate(attributes);
        return switch (decision) {
            case AccessDecision.Allow _ -> {
                Instant now = Instant.now();
                Instant expiresAt = defaultTtl != null ? now.plus(defaultTtl) : null;
                DelegationGrant grant = new DelegationGrant(
                    UUID.randomUUID().toString(), request.scope(), request.controlMode(),
                    now, expiresAt);
                yield new SpawnResult.Approved(new AgentSession(UUID.randomUUID().toString(), grant));
            }
            case AccessDecision.Deny d -> new SpawnResult.Denied(d.reason());
        };
    }

    private Attributes buildAttributes(SpawnRequest request, Lookup lookup) {
        Attributes.Builder b = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "agent:spawn")
            .put(AttributeKeys.ACTION_TYPE, "execute")
            .put(AttributeKeys.CONTROL_MODE, request.controlMode().name().toLowerCase())
            .put(AttributeKeys.CONTROL_CHANNEL, "agent_intent")
            .put(AttributeKeys.CONTEXT_TIME, Instant.now());

        if (lookup != null) {
            b.put(AttributeKeys.SUBJECT_TYPE, "user");
            b.put(AttributeKeys.SUBJECT_USER_ID, lookup.get(ContextKeys.AUTH_USER));
            b.put(AttributeKeys.SUBJECT_ROLES, lookup.get(ContextKeys.AUTH_ROLES));
            b.put(AttributeKeys.CONTROL_USER_PRESENT, true);
            QualifiedSessionId sessionId = lookup.get(QualifiedSessionId.class);
            if (sessionId != null) {
                b.put(AttributeKeys.CONTEXT_SESSION_ID, sessionId.sessionId());
            }
        }

        return b.build();
    }
}
