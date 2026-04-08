package rsp.compositions.agent;

import rsp.component.Lookup;
import rsp.page.QualifiedSessionId;

import java.util.Objects;
import java.util.UUID;

/**
 * {@link AgentSpawner} decorator that requires user approval before delegating.
 * <p>
 * Checks a {@link DelegationStore} for an existing decision:
 * <ul>
 *   <li>Approved → delegates to inner spawner</li>
 *   <li>Denied → returns {@link SpawnResult.Denied}</li>
 *   <li>No decision → returns {@link SpawnResult.RequiresApproval}</li>
 * </ul>
 */
public final class ApprovalSpawner implements AgentSpawner {
    private final AgentSpawner inner;
    private final DelegationStore store;

    public ApprovalSpawner(AgentSpawner inner, DelegationStore store) {
        this.inner = Objects.requireNonNull(inner);
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public SpawnResult spawn(SpawnRequest request, Lookup lookup) {
        String sessionKey = resolveSessionKey(lookup);

        DelegationStore.Decision decision = store.getDecision(sessionKey);
        if (decision != null) {
            if (decision.approved()) {
                return inner.spawn(request, lookup);
            } else {
                return new SpawnResult.Denied("User denied agent delegation");
            }
        }

        String ticketId = UUID.randomUUID().toString();
        String reason = buildReason(request);
        return new SpawnResult.RequiresApproval(ticketId, reason);
    }

    private String resolveSessionKey(Lookup lookup) {
        if (lookup != null) {
            QualifiedSessionId qsid = lookup.get(QualifiedSessionId.class);
            if (qsid != null) {
                return qsid.sessionId();
            }
        }
        return "unknown-session";
    }

    private String buildReason(SpawnRequest request) {
        StringBuilder sb = new StringBuilder("Agent requests delegation: ");
        sb.append("scope=").append(request.scope());
        sb.append(", mode=").append(request.controlMode());
        if (request.purpose() != null) {
            sb.append(", purpose=").append(request.purpose());
        }
        return sb.toString();
    }
}
