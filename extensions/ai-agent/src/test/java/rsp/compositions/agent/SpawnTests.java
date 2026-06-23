package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.DelegationGrant;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SpawnTests {

    // --- SpawnRequest ---

    @Test
    void spawnRequest_nullScope_throws() {
        assertThrows(NullPointerException.class,
            () -> new SpawnRequest(null, ControlMode.ASSIST, null));
    }

    @Test
    void spawnRequest_nullControlMode_throws() {
        assertThrows(NullPointerException.class,
            () -> new SpawnRequest(AgentContext.Scope.APP, null, null));
    }

    @Test
    void spawnRequest_nullPurpose_allowed() {
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        assertNull(request.purpose());
    }

    @Test
    void spawnRequest_carries_values() {
        SpawnRequest request = new SpawnRequest(
            AgentContext.Scope.CONTRACT, ControlMode.AUTOPLAY, "testing");
        assertEquals(AgentContext.Scope.CONTRACT, request.scope());
        assertEquals(ControlMode.AUTOPLAY, request.controlMode());
        assertEquals("testing", request.purpose());
    }

    // --- DelegationGrant ---

    @Test
    void grant_notExpired_when_no_expiry() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        assertFalse(grant.isExpired());
    }

    @Test
    void grant_notExpired_when_future_expiry() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(),
            Instant.now(), Instant.now().plusSeconds(3600));
        assertFalse(grant.isExpired());
    }

    @Test
    void grant_expired_when_past_expiry() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(),
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        assertTrue(grant.isExpired());
    }

    @Test
    void grant_nullGrantId_throws() {
        assertThrows(NullPointerException.class,
            () -> new DelegationGrant(null, Attributes.empty(), Instant.now(), null));
    }

    // --- AgentSession ---

    @Test
    void session_valid_when_grant_not_expired() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        AgentSession session = new AgentSession("s1", grant);
        assertTrue(session.isValid());
    }

    @Test
    void session_invalid_when_grant_expired() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(),
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        AgentSession session = new AgentSession("s1", grant);
        assertFalse(session.isValid());
    }

    @Test
    void session_nullSessionId_throws() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        assertThrows(NullPointerException.class,
            () -> new AgentSession(null, grant));
    }

    @Test
    void session_nullGrant_throws() {
        assertThrows(NullPointerException.class,
            () -> new AgentSession("s1", null));
    }

    // --- SpawnResult ---

    @Test
    void approved_carries_session() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        AgentSession session = new AgentSession("s1", grant);
        SpawnResult result = new SpawnResult.Approved(session);
        assertInstanceOf(SpawnResult.Approved.class, result);
        assertSame(session, ((SpawnResult.Approved) result).session());
    }

    @Test
    void denied_carries_reason() {
        SpawnResult result = new SpawnResult.Denied("Insufficient permissions");
        assertInstanceOf(SpawnResult.Denied.class, result);
        assertEquals("Insufficient permissions", ((SpawnResult.Denied) result).reason());
    }

    @Test
    void requiresApproval_carries_ticket() {
        SpawnResult result = new SpawnResult.RequiresApproval("ticket-123", "Needs admin review");
        assertInstanceOf(SpawnResult.RequiresApproval.class, result);
        assertEquals("ticket-123", ((SpawnResult.RequiresApproval) result).ticketId());
        assertEquals("Needs admin review", ((SpawnResult.RequiresApproval) result).reason());
    }

    @Test
    void exhaustive_switch() {
        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        SpawnResult result = new SpawnResult.Approved(new AgentSession("s1", grant));
        String outcome = switch (result) {
            case SpawnResult.Approved a -> "approved";
            case SpawnResult.Denied d -> "denied";
            case SpawnResult.RequiresApproval r -> "pending";
        };
        assertEquals("approved", outcome);
    }

    // --- AllowAllSpawner ---

    @Test
    void allowAllSpawner_always_approves() {
        AllowAllSpawner spawner = new AllowAllSpawner();
        SpawnRequest request = new SpawnRequest(
            AgentContext.Scope.APP, ControlMode.ASSIST, "test");
        SpawnResult result = spawner.spawn(request, null);
        assertInstanceOf(SpawnResult.Approved.class, result);
    }

    @Test
    void allowAllSpawner_session_has_valid_grant() {
        AllowAllSpawner spawner = new AllowAllSpawner();
        SpawnRequest request = new SpawnRequest(
            AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult.Approved approved = (SpawnResult.Approved) spawner.spawn(request, null);
        AgentSession session = approved.session();
        assertTrue(session.isValid());
        assertNotNull(session.sessionId());
        assertNotNull(session.grant().grantId());
        assertEquals("assist", session.grant().entitlements().getString(AttributeKeys.CONTROL_MODE));
        assertNull(session.grant().expiresAt());
    }

    @Test
    void allowAllSpawner_generates_unique_ids() {
        AllowAllSpawner spawner = new AllowAllSpawner();
        SpawnRequest request = new SpawnRequest(
            AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult.Approved a1 = (SpawnResult.Approved) spawner.spawn(request, null);
        SpawnResult.Approved a2 = (SpawnResult.Approved) spawner.spawn(request, null);
        assertNotEquals(a1.session().sessionId(), a2.session().sessionId());
        assertNotEquals(a1.session().grant().grantId(), a2.session().grant().grantId());
    }
}
