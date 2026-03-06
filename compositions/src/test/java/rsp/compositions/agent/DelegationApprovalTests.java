package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.page.QualifiedSessionId;
import rsp.compositions.application.TestLookup;
import rsp.compositions.contract.ContextKeys;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DelegationApprovalTests {

    private static final String SESSION_KEY = "test-session-1";

    // --- DelegationStore ---

    @Test
    void store_returns_null_when_no_decision() {
        DelegationStore store = new InMemoryDelegationStore();
        assertNull(store.getDecision("nonexistent"));
    }

    @Test
    void store_records_approved_decision() {
        DelegationStore store = new InMemoryDelegationStore();
        store.recordDecision(SESSION_KEY, true);
        DelegationStore.Decision decision = store.getDecision(SESSION_KEY);
        assertNotNull(decision);
        assertTrue(decision.approved());
        assertEquals(SESSION_KEY, decision.sessionKey());
    }

    @Test
    void store_records_denied_decision() {
        DelegationStore store = new InMemoryDelegationStore();
        store.recordDecision(SESSION_KEY, false);
        DelegationStore.Decision decision = store.getDecision(SESSION_KEY);
        assertNotNull(decision);
        assertFalse(decision.approved());
    }

    @Test
    void store_overwrites_previous_decision() {
        DelegationStore store = new InMemoryDelegationStore();
        store.recordDecision(SESSION_KEY, true);
        store.recordDecision(SESSION_KEY, false);
        assertFalse(store.getDecision(SESSION_KEY).approved());
    }

    @Test
    void store_removes_decision() {
        DelegationStore store = new InMemoryDelegationStore();
        store.recordDecision(SESSION_KEY, true);
        store.removeDecision(SESSION_KEY);
        assertNull(store.getDecision(SESSION_KEY));
    }

    @Test
    void store_isolates_sessions() {
        DelegationStore store = new InMemoryDelegationStore();
        store.recordDecision("session-a", true);
        store.recordDecision("session-b", false);
        assertTrue(store.getDecision("session-a").approved());
        assertFalse(store.getDecision("session-b").approved());
    }

    // --- ApprovalSpawner ---

    @Test
    void approvalSpawner_no_decision_returns_requiresApproval() {
        DelegationStore store = new InMemoryDelegationStore();
        AgentSpawner inner = new AllowAllSpawner();
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult result = spawner.spawn(request, null);

        assertInstanceOf(SpawnResult.RequiresApproval.class, result);
        SpawnResult.RequiresApproval pending = (SpawnResult.RequiresApproval) result;
        assertNotNull(pending.ticketId());
        assertFalse(pending.ticketId().isEmpty());
        assertTrue(pending.reason().contains("scope=APP"));
        assertTrue(pending.reason().contains("mode=ASSIST"));
    }

    @Test
    void approvalSpawner_approved_delegates_to_inner() {
        DelegationStore store = new InMemoryDelegationStore();
        AgentSpawner inner = new AllowAllSpawner();
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        TestLookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));
        store.recordDecision(SESSION_KEY, true);

        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult result = spawner.spawn(request, lookup);

        assertInstanceOf(SpawnResult.Approved.class, result);
        AgentSession session = ((SpawnResult.Approved) result).session();
        assertTrue(session.isValid());
    }

    @Test
    void approvalSpawner_denied_returns_denied() {
        DelegationStore store = new InMemoryDelegationStore();
        AgentSpawner inner = new AllowAllSpawner();
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        TestLookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));
        store.recordDecision(SESSION_KEY, false);

        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult result = spawner.spawn(request, lookup);

        assertInstanceOf(SpawnResult.Denied.class, result);
        assertEquals("User denied agent delegation",
                ((SpawnResult.Denied) result).reason());
    }

    @Test
    void approvalSpawner_denied_does_not_call_inner() {
        DelegationStore store = new InMemoryDelegationStore();
        boolean[] innerCalled = {false};
        AgentSpawner inner = (req, lkp) -> {
            innerCalled[0] = true;
            return new SpawnResult.Approved(
                    new AgentSession("s1", new DelegationGrant(
                            "g1", AgentContext.Scope.APP, ControlMode.ASSIST,
                            java.time.Instant.now(), null)));
        };
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        TestLookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));
        store.recordDecision(SESSION_KEY, false);

        spawner.spawn(new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null), lookup);

        assertFalse(innerCalled[0]);
    }

    @Test
    void approvalSpawner_inner_denied_propagated_after_approval() {
        DelegationStore store = new InMemoryDelegationStore();
        AgentSpawner inner = new PolicySpawner(ExamplePolicies.denyAll());
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        TestLookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));
        store.recordDecision(SESSION_KEY, true);

        SpawnResult result = spawner.spawn(new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null), lookup);

        assertInstanceOf(SpawnResult.Denied.class, result);
    }

    @Test
    void approvalSpawner_null_lookup_uses_fallback_key() {
        DelegationStore store = new InMemoryDelegationStore();
        AgentSpawner inner = new AllowAllSpawner();
        ApprovalSpawner spawner = new ApprovalSpawner(inner, store);

        // No decision for "unknown-session" → RequiresApproval
        SpawnResult result = spawner.spawn(new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null), null);
        assertInstanceOf(SpawnResult.RequiresApproval.class, result);

        // Record for fallback key → Approved
        store.recordDecision("unknown-session", true);
        result = spawner.spawn(new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.ASSIST, null), null);
        assertInstanceOf(SpawnResult.Approved.class, result);
    }

    @Test
    void approvalSpawner_reason_includes_purpose() {
        DelegationStore store = new InMemoryDelegationStore();
        ApprovalSpawner spawner = new ApprovalSpawner(new AllowAllSpawner(), store);

        SpawnRequest request = new SpawnRequest(
                AgentContext.Scope.APP, ControlMode.AUTOPLAY, "admin tasks");
        SpawnResult result = spawner.spawn(request, null);

        assertInstanceOf(SpawnResult.RequiresApproval.class, result);
        String reason = ((SpawnResult.RequiresApproval) result).reason();
        assertTrue(reason.contains("purpose=admin tasks"));
        assertTrue(reason.contains("mode=AUTOPLAY"));
    }

    // --- DelegationApprovalContract ---

    @Test
    void contract_reads_show_data() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.SHOW_DATA,
                        Map.of("scope", "APP", "controlMode", "ASSIST", "reason", "testing"))
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        DelegationApprovalContract contract = new DelegationApprovalContract(lookup, store);
        assertEquals("Agent Delegation Approval", contract.title());
    }

    @Test
    void contract_user_decision_true_saves_approved() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.SHOW_DATA,
                        Map.of("scope", "APP", "controlMode", "ASSIST", "reason", ""))
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        new DelegationApprovalContract(lookup, store);

        // Simulate user clicking Approve
        lookup.publish(DelegationApprovalContract.USER_DECISION, true);

        DelegationStore.Decision decision = store.getDecision(SESSION_KEY);
        assertNotNull(decision);
        assertTrue(decision.approved());
    }

    @Test
    void contract_user_decision_false_saves_denied() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.SHOW_DATA,
                        Map.of("scope", "APP", "controlMode", "ASSIST", "reason", ""))
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        new DelegationApprovalContract(lookup, store);

        lookup.publish(DelegationApprovalContract.USER_DECISION, false);

        DelegationStore.Decision decision = store.getDecision(SESSION_KEY);
        assertNotNull(decision);
        assertFalse(decision.approved());
    }

    @Test
    void contract_emits_approval_decided_event() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.SHOW_DATA,
                        Map.of("scope", "APP", "controlMode", "ASSIST", "reason", ""))
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        new DelegationApprovalContract(lookup, store);

        lookup.publish(DelegationApprovalContract.USER_DECISION, true);

        assertTrue(lookup.wasPublished(DelegationApprovalContract.APPROVAL_DECIDED));
        Boolean payload = lookup.getLastPublishedPayload(
                DelegationApprovalContract.APPROVAL_DECIDED);
        assertTrue(payload);
    }

    @Test
    void contract_emits_action_success_to_close_modal() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(ContextKeys.SHOW_DATA,
                        Map.of("scope", "APP", "controlMode", "ASSIST", "reason", ""))
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        new DelegationApprovalContract(lookup, store);

        lookup.publish(DelegationApprovalContract.USER_DECISION, false);

        assertTrue(lookup.wasPublished(
                rsp.compositions.contract.EventKeys.ACTION_SUCCESS));
    }

    @Test
    void contract_defaults_when_no_show_data() {
        DelegationStore store = new InMemoryDelegationStore();
        TestLookup lookup = new TestLookup()
                .withData(QualifiedSessionId.class,
                        new QualifiedSessionId("device-1", SESSION_KEY));

        DelegationApprovalContract contract = new DelegationApprovalContract(lookup, store);
        assertEquals("Agent Delegation Approval", contract.title());
    }
}
