package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.DelegationGrant;

import java.time.Instant;
import java.util.UUID;

/**
 * Development-mode spawner that always approves with an unlimited grant.
 * <p>
 * Analogous to {@code AllowAllGate} — suitable for development and examples.
 */
public final class AllowAllSpawner implements AgentSpawner {
    @Override
    public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
        final String grantId = UUID.randomUUID().toString();
        final String sessionId = UUID.randomUUID().toString();
        Attributes entitlements = Attributes.builder()
            .put(rsp.compositions.authorization.AttributeKeys.CONTROL_MODE,
                 request.controlMode().name().toLowerCase())
            .build();
        final DelegationGrant grant = new DelegationGrant(
            grantId, entitlements, Instant.now(), null
        );
        return new SpawnResult.Approved(new AgentSession(sessionId, grant));
    }
}
