package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;


import rsp.compositions.contract.ContractAction;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.authorization.AccessDecision;
import rsp.compositions.authorization.AccessPolicy;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.AttributeKeys;
import rsp.compositions.authorization.Authorization;
import rsp.compositions.authorization.CompositePolicy;
import rsp.compositions.authorization.DelegationGrant;
import rsp.compositions.authorization.ExamplePolicies;
import rsp.compositions.authorization.GrantConstraints;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTests {

    private static final EventKey.VoidKey DUMMY_KEY = new EventKey.VoidKey("test.dummy");

    // --- AccessDecision ---

    @Test
    void accessDecision_allow() {
        AccessDecision decision = new AccessDecision.Allow();
        assertInstanceOf(AccessDecision.Allow.class, decision);
    }

    @Test
    void accessDecision_deny_carries_reason() {
        AccessDecision decision = new AccessDecision.Deny("forbidden");
        assertInstanceOf(AccessDecision.Deny.class, decision);
        assertEquals("forbidden", ((AccessDecision.Deny) decision).reason());
    }

    @Test
    void accessDecision_exhaustive_switch() {
        AccessDecision decision = new AccessDecision.Allow();
        String outcome = switch (decision) {
            case AccessDecision.Allow a -> "allow";
            case AccessDecision.Deny d -> "deny";
        };
        assertEquals("allow", outcome);
    }

    // --- Attributes ---

    @Test
    void attributes_builder_skips_nulls() {
        Attributes attrs = Attributes.builder()
            .put("a", "value")
            .put("b", null)
            .build();
        assertEquals("value", attrs.getString("a"));
        assertFalse(attrs.hasKey("b"));
    }

    @Test
    void attributes_defensive_copy() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("key", "original");
        Attributes attrs = new Attributes(map);
        map.put("key", "mutated");
        assertEquals("original", attrs.getString("key"));
    }

    @Test
    void attributes_getString_returns_null_for_non_string() {
        Attributes attrs = Attributes.builder().put("num", 42).build();
        assertNull(attrs.getString("num"));
    }

    @Test
    void attributes_getTyped() {
        Attributes attrs = Attributes.builder()
            .put("mode", ControlMode.ASSIST)
            .build();
        assertEquals(ControlMode.ASSIST, attrs.getTyped("mode", ControlMode.class));
        assertNull(attrs.getTyped("mode", String.class));
        assertNull(attrs.getTyped("missing", ControlMode.class));
    }

    @Test
    void attributes_hasKey() {
        Attributes attrs = Attributes.builder().put("exists", "yes").build();
        assertTrue(attrs.hasKey("exists"));
        assertFalse(attrs.hasKey("missing"));
    }

    @Test
    void attributes_merge_combines_maps() {
        Attributes a = Attributes.builder().put("x", 1).put("y", 2).build();
        Attributes b = Attributes.builder().put("z", 3).build();
        Attributes merged = a.merge(b);
        assertEquals(1, merged.get("x"));
        assertEquals(2, merged.get("y"));
        assertEquals(3, merged.get("z"));
    }

    @Test
    void attributes_merge_other_wins_on_conflict() {
        Attributes a = Attributes.builder().put("k", "old").build();
        Attributes b = Attributes.builder().put("k", "new").build();
        assertEquals("new", a.merge(b).getString("k"));
    }

    @Test
    void attributes_extend_adds_single_key() {
        Attributes attrs = Attributes.builder().put("a", 1).build();
        Attributes extended = attrs.extend("b", 2);
        assertEquals(1, extended.get("a"));
        assertEquals(2, extended.get("b"));
    }

    @Test
    void attributes_empty() {
        Attributes empty = Attributes.empty();
        assertTrue(empty.values().isEmpty());
    }

    // --- CompositePolicy ---

    @Test
    void compositePolicy_empty_allows() {
        CompositePolicy policy = new CompositePolicy();
        AccessDecision decision = policy.evaluate(Attributes.builder().build());
        assertInstanceOf(AccessDecision.Allow.class, decision);
    }

    @Test
    void compositePolicy_first_deny_wins() {
        CompositePolicy policy = new CompositePolicy(
            ExamplePolicies.allowAll(),
            ExamplePolicies.denyAll(),
            ExamplePolicies.allowAll()
        );
        AccessDecision decision = policy.evaluate(Attributes.builder().build());
        assertInstanceOf(AccessDecision.Deny.class, decision);
    }

    @Test
    void compositePolicy_all_allow_passes() {
        CompositePolicy policy = new CompositePolicy(
            ExamplePolicies.allowAll(),
            ExamplePolicies.allowAll()
        );
        AccessDecision decision = policy.evaluate(Attributes.builder().build());
        assertInstanceOf(AccessDecision.Allow.class, decision);
    }

    // --- GrantConstraints ---

    @Test
    void grantConstraints_no_grant_attributes_passes() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "navigate")
            .build();
        assertNull(GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_revoked_fails() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_REVOKED, true)
            .build();
        assertEquals("Grant has been revoked", GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_expired_fails() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_EXPIRES_AT, Instant.now().minusSeconds(3600))
            .build();
        assertEquals("Grant expired", GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_not_expired_passes() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_EXPIRES_AT, Instant.now().plusSeconds(3600))
            .build();
        assertNull(GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_action_not_in_scope_fails() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("navigate", "page"))
            .put(AttributeKeys.ACTION_NAME, "delete")
            .build();
        assertEquals("Action 'delete' not in grant scope", GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_action_in_scope_passes() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("navigate", "page"))
            .put(AttributeKeys.ACTION_NAME, "navigate")
            .build();
        assertNull(GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_control_mode_not_in_scope_fails() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_CONTROL_MODES, Set.of("assist"))
            .put(AttributeKeys.CONTROL_MODE, "autoplay")
            .build();
        assertEquals("Control mode 'autoplay' not in grant scope", GrantConstraints.check(attrs));
    }

    @Test
    void grantConstraints_control_mode_in_scope_passes() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_CONTROL_MODES, Set.of("assist", "autoplay"))
            .put(AttributeKeys.CONTROL_MODE, "assist")
            .build();
        assertNull(GrantConstraints.check(attrs));
    }

    // --- PolicySpawner ---

    @Test
    void policySpawner_allow_returns_approved() {
        Authorization auth = new Authorization(ExamplePolicies.allowAll(), Attributes.empty());
        PolicySpawner spawner = new PolicySpawner(auth);
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult result = spawner.spawn(request, null);
        assertInstanceOf(SpawnResult.Approved.class, result);
        AgentSession session = ((SpawnResult.Approved) result).session();
        assertTrue(session.isValid());
    }

    @Test
    void policySpawner_deny_returns_denied() {
        Authorization auth = new Authorization(ExamplePolicies.denyAll(), Attributes.empty());
        PolicySpawner spawner = new PolicySpawner(auth);
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult result = spawner.spawn(request, null);
        assertInstanceOf(SpawnResult.Denied.class, result);
        assertEquals("Denied by policy", ((SpawnResult.Denied) result).reason());
    }

    @Test
    void policySpawner_ttl_applied() {
        Authorization auth = new Authorization(ExamplePolicies.allowAll(), Attributes.empty());
        PolicySpawner spawner = new PolicySpawner(auth, Duration.ofHours(1));
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult.Approved approved = (SpawnResult.Approved) spawner.spawn(request, null);
        assertNotNull(approved.session().grant().expiresAt());
    }

    @Test
    void policySpawner_no_ttl_means_no_expiry() {
        Authorization auth = new Authorization(ExamplePolicies.allowAll(), Attributes.empty());
        PolicySpawner spawner = new PolicySpawner(auth);
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult.Approved approved = (SpawnResult.Approved) spawner.spawn(request, null);
        assertNull(approved.session().grant().expiresAt());
    }

    // --- PolicyActionFilter ---

    @Test
    void policyActionFilter_filters_by_policy() {
        AccessPolicy readOnly = attrs -> {
            String actionName = attrs.getString(AttributeKeys.ACTION_NAME);
            return "page".equals(actionName)
                ? new AccessDecision.Allow()
                : new AccessDecision.Deny("blocked");
        };
        Authorization auth = new Authorization(readOnly, Attributes.empty());
        PolicyActionFilter filter = new PolicyActionFilter(auth);

        ContractAction pageAction = new ContractAction("page", DUMMY_KEY, "Go to page");
        ContractAction deleteAction = new ContractAction("delete", DUMMY_KEY, "Delete items");

        List<ContractAction> result = filter.filter(List.of(pageAction, deleteAction), null);
        assertEquals(1, result.size());
        assertEquals("page", result.getFirst().action());
    }

    @Test
    void policyActionFilter_allow_all_passes_everything() {
        Authorization auth = new Authorization(ExamplePolicies.allowAll(), Attributes.empty());
        PolicyActionFilter filter = new PolicyActionFilter(auth);
        ContractAction a1 = new ContractAction("page", DUMMY_KEY, "page");
        ContractAction a2 = new ContractAction("delete", DUMMY_KEY, "delete");
        assertEquals(2, filter.filter(List.of(a1, a2), null).size());
    }

    @Test
    void policyActionFilter_deny_all_filters_everything() {
        Authorization auth = new Authorization(ExamplePolicies.denyAll(), Attributes.empty());
        PolicyActionFilter filter = new PolicyActionFilter(auth);
        ContractAction a1 = new ContractAction("page", DUMMY_KEY, "page");
        assertEquals(0, filter.filter(List.of(a1), null).size());
    }

    // --- PolicyGate ---

    @Test
    void policyGate_allow_maps_to_gateResult_allow() {
        Authorization auth = new Authorization(ExamplePolicies.allowAll(), Attributes.empty());
        PolicyGate gate = new PolicyGate(auth);
        ContractAction action = new ContractAction("navigate", DUMMY_KEY, "Navigate");
        GateResult result = gate.evaluate(action, ContractActionPayload.EMPTY, null);
        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(action, ((GateResult.Allow) result).action());
    }

    @Test
    void policyGate_deny_maps_to_gateResult_block() {
        Authorization auth = new Authorization(ExamplePolicies.denyAll(), Attributes.empty());
        PolicyGate gate = new PolicyGate(auth);
        ContractAction action = new ContractAction("delete", DUMMY_KEY, "Delete");
        GateResult result = gate.evaluate(action, ContractActionPayload.EMPTY, null);
        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Denied by policy", ((GateResult.Block) result).reason());
    }

    @Test
    void policyGate_with_grant_constraints() {
        AccessPolicy policy = ExamplePolicies.grantConstraints();
        Authorization auth = new Authorization(policy, Attributes.empty());
        DelegationGrant expiredGrant = new DelegationGrant(
            "g1", Attributes.empty(),
            Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        Authorization agentAuth = auth.delegated(expiredGrant);
        PolicyGate gate = new PolicyGate(agentAuth);
        ContractAction navAction = new ContractAction("navigate", DUMMY_KEY, "Navigate");
        GateResult result = gate.evaluate(navAction, ContractActionPayload.EMPTY, null);
        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Grant expired", ((GateResult.Block) result).reason());
    }

    // --- ExamplePolicies ---

    @Test
    void requireAuthenticated_allows_with_user_id() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.SUBJECT_USER_ID, "user-1")
            .build();
        assertInstanceOf(AccessDecision.Allow.class,
            ExamplePolicies.requireAuthenticated().evaluate(attrs));
    }

    @Test
    void requireAuthenticated_denies_without_user_id() {
        Attributes attrs = Attributes.builder().build();
        assertInstanceOf(AccessDecision.Deny.class,
            ExamplePolicies.requireAuthenticated().evaluate(attrs));
    }

    @Test
    void readOnly_allows_safe_actions() {
        AccessPolicy policy = ExamplePolicies.readOnly();
        for (String safe : List.of("navigate", "page", "select_all")) {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.ACTION_NAME, safe)
                .build();
            assertInstanceOf(AccessDecision.Allow.class, policy.evaluate(attrs),
                "Expected allow for action: " + safe);
        }
    }

    @Test
    void readOnly_allows_read_type() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.ACTION_TYPE, "read")
            .put(AttributeKeys.ACTION_NAME, "custom_read")
            .build();
        assertInstanceOf(AccessDecision.Allow.class,
            ExamplePolicies.readOnly().evaluate(attrs));
    }

    @Test
    void readOnly_denies_write_actions() {
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "delete")
            .put(AttributeKeys.ACTION_TYPE, "delete")
            .build();
        assertInstanceOf(AccessDecision.Deny.class,
            ExamplePolicies.readOnly().evaluate(attrs));
    }

    // --- Authorization ---

    @Test
    void authorization_merges_subject_and_action_attributes() {
        Attributes subject = Attributes.builder()
            .put(AttributeKeys.SUBJECT_USER_ID, "user-1")
            .build();
        AccessPolicy policy = attrs -> attrs.hasKey(AttributeKeys.SUBJECT_USER_ID)
                && attrs.hasKey(AttributeKeys.ACTION_NAME)
            ? new AccessDecision.Allow()
            : new AccessDecision.Deny("missing attrs");

        Authorization auth = new Authorization(policy, subject);
        Attributes action = Attributes.builder().put(AttributeKeys.ACTION_NAME, "edit").build();
        assertInstanceOf(AccessDecision.Allow.class, auth.evaluate(action));
    }

    @Test
    void authorization_delegated_merges_grant_attributes() {
        AccessPolicy policy = ExamplePolicies.grantConstraints();
        Authorization auth = new Authorization(policy, Attributes.empty());

        DelegationGrant grant = new DelegationGrant(
            "g1", Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("navigate"))
                .build(),
            Instant.now(), null);

        Authorization agentAuth = auth.delegated(grant);
        // "navigate" is in scope — should allow
        Attributes navigateAction = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "navigate").build();
        assertInstanceOf(AccessDecision.Allow.class, agentAuth.evaluate(navigateAction));

        // "delete" is not in scope — should deny
        Attributes deleteAction = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "delete").build();
        assertInstanceOf(AccessDecision.Deny.class, agentAuth.evaluate(deleteAction));
    }

    @Test
    void authorization_scoped_restricts_actions() {
        AccessPolicy policy = ExamplePolicies.grantConstraints();
        Authorization auth = new Authorization(policy, Attributes.empty());

        Authorization scoped = auth.scoped(Set.of("read:game_state", "write:level_data"));
        Attributes allowed = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "read:game_state").build();
        assertInstanceOf(AccessDecision.Allow.class, scoped.evaluate(allowed));

        Attributes denied = Attributes.builder()
            .put(AttributeKeys.ACTION_NAME, "delete_everything").build();
        assertInstanceOf(AccessDecision.Deny.class, scoped.evaluate(denied));
    }

    // --- Integration: composite policy through all 3 adapters ---

    @Test
    void integration_composite_policy_across_adapters() {
        // Policy: must be authenticated + grant constraints
        AccessPolicy policy = new CompositePolicy(
            ExamplePolicies.requireAuthenticated(),
            ExamplePolicies.grantConstraints()
        );
        Authorization auth = new Authorization(policy, Attributes.empty());

        // Spawner: deny when not authenticated
        PolicySpawner spawner = new PolicySpawner(auth);
        SpawnRequest request = new SpawnRequest(AgentContext.Scope.APP, ControlMode.ASSIST, null);
        SpawnResult spawnResult = spawner.spawn(request, null);
        assertInstanceOf(SpawnResult.Denied.class, spawnResult);

        // Gate with valid grant: deny when not authenticated (no subject in attrs)
        DelegationGrant validGrant = new DelegationGrant(
            "g1", Attributes.empty(), Instant.now(), null);
        Authorization agentAuth = auth.delegated(validGrant);
        PolicyGate gate = new PolicyGate(agentAuth);
        ContractAction deleteAction = new ContractAction("delete", DUMMY_KEY, "Delete");
        GateResult gateResult = gate.evaluate(deleteAction, ContractActionPayload.EMPTY, null);
        assertInstanceOf(GateResult.Block.class, gateResult);

        // Filter with valid grant: deny all when not authenticated
        PolicyActionFilter filter = new PolicyActionFilter(agentAuth);
        ContractAction action = new ContractAction("page", DUMMY_KEY, "page");
        assertEquals(0, filter.filter(List.of(action), null).size());
    }

    @Test
    void integration_grant_scope_restricts_actions() {
        // Policy: grant constraints only
        AccessPolicy policy = ExamplePolicies.grantConstraints();

        // Grant scoped to only navigate and page
        Attributes attrs = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("navigate", "page"))
            .put(AttributeKeys.ACTION_NAME, "delete")
            .build();
        assertInstanceOf(AccessDecision.Deny.class, policy.evaluate(attrs));

        // Same grant, allowed action
        Attributes allowed = Attributes.builder()
            .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("navigate", "page"))
            .put(AttributeKeys.ACTION_NAME, "navigate")
            .build();
        assertInstanceOf(AccessDecision.Allow.class, policy.evaluate(allowed));
    }
}
