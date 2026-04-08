package rsp.compositions.authorization;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationTests {

    // =====================
    // Attributes
    // =====================

    @Nested
    class AttributesTests {

        @Test
        void empty_attributes_has_no_values() {
            Attributes attrs = Attributes.empty();
            assertTrue(attrs.values().isEmpty());
            assertNull(attrs.get("any.key"));
        }

        @Test
        void get_returns_stored_value() {
            Attributes attrs = new Attributes(Map.of("subject.user_id", "alice"));
            assertEquals("alice", attrs.get("subject.user_id"));
        }

        @Test
        void getString_returns_string_value() {
            Attributes attrs = new Attributes(Map.of("subject.user_id", "alice"));
            assertEquals("alice", attrs.getString("subject.user_id"));
        }

        @Test
        void getString_returns_null_for_non_string() {
            Attributes attrs = new Attributes(Map.of("count", 42));
            assertNull(attrs.getString("count"));
        }

        @Test
        void getTyped_returns_value_of_correct_type() {
            Attributes attrs = new Attributes(Map.of("count", 42));
            assertEquals(42, attrs.getTyped("count", Integer.class));
        }

        @Test
        void getTyped_returns_null_for_wrong_type() {
            Attributes attrs = new Attributes(Map.of("count", 42));
            assertNull(attrs.getTyped("count", String.class));
        }

        @Test
        void hasKey_true_for_present_key() {
            Attributes attrs = new Attributes(Map.of("key", "value"));
            assertTrue(attrs.hasKey("key"));
        }

        @Test
        void hasKey_false_for_absent_key() {
            Attributes attrs = Attributes.empty();
            assertFalse(attrs.hasKey("key"));
        }

        @Test
        void merge_combines_attributes() {
            Attributes a = new Attributes(Map.of("a", 1));
            Attributes b = new Attributes(Map.of("b", 2));
            Attributes merged = a.merge(b);

            assertEquals(1, merged.get("a"));
            assertEquals(2, merged.get("b"));
        }

        @Test
        void merge_later_overrides_earlier() {
            Attributes a = new Attributes(Map.of("key", "old"));
            Attributes b = new Attributes(Map.of("key", "new"));
            Attributes merged = a.merge(b);

            assertEquals("new", merged.get("key"));
        }

        @Test
        void extend_adds_key() {
            Attributes attrs = Attributes.empty().extend("key", "value");
            assertEquals("value", attrs.get("key"));
        }

        @Test
        void extend_ignores_null_value() {
            Attributes attrs = Attributes.empty().extend("key", null);
            assertFalse(attrs.hasKey("key"));
        }

        @Test
        void immutable_after_creation() {
            Map<String, Object> mutable = new java.util.HashMap<>();
            mutable.put("key", "value");
            Attributes attrs = new Attributes(mutable);
            mutable.put("key", "changed");
            assertEquals("value", attrs.get("key"));
        }

        @Test
        void builder_creates_attributes() {
            Attributes attrs = Attributes.builder()
                .put("a", 1)
                .put("b", "two")
                .build();

            assertEquals(1, attrs.get("a"));
            assertEquals("two", attrs.get("b"));
        }

        @Test
        void builder_ignores_null_values() {
            Attributes attrs = Attributes.builder()
                .put("a", null)
                .build();

            assertFalse(attrs.hasKey("a"));
        }
    }

    // =====================
    // AccessDecision
    // =====================

    @Nested
    class AccessDecisionTests {

        @Test
        void allow_is_allow() {
            AccessDecision decision = new AccessDecision.Allow();
            assertInstanceOf(AccessDecision.Allow.class, decision);
        }

        @Test
        void deny_carries_reason() {
            AccessDecision decision = new AccessDecision.Deny("not authorized");
            assertInstanceOf(AccessDecision.Deny.class, decision);
            assertEquals("not authorized", ((AccessDecision.Deny) decision).reason());
        }
    }

    // =====================
    // AccessPolicy
    // =====================

    @Nested
    class AccessPolicyTests {

        @Test
        void functional_interface_allows_lambda() {
            AccessPolicy policy = _ -> new AccessDecision.Allow();
            assertInstanceOf(AccessDecision.Allow.class, policy.evaluate(Attributes.empty()));
        }

        @Test
        void policy_can_inspect_attributes() {
            AccessPolicy policy = attrs ->
                attrs.hasKey(AttributeKeys.SUBJECT_USER_ID)
                    ? new AccessDecision.Allow()
                    : new AccessDecision.Deny("no user");

            assertInstanceOf(AccessDecision.Deny.class, policy.evaluate(Attributes.empty()));
            assertInstanceOf(AccessDecision.Allow.class,
                policy.evaluate(new Attributes(Map.of(AttributeKeys.SUBJECT_USER_ID, "alice"))));
        }
    }

    // =====================
    // Authorization
    // =====================

    @Nested
    class AuthorizationContextTests {

        @Test
        void evaluate_merges_subject_and_action_attributes() {
            AccessPolicy policy = attrs -> {
                if ("alice".equals(attrs.getString(AttributeKeys.SUBJECT_USER_ID))
                    && "delete".equals(attrs.getString(AttributeKeys.ACTION_NAME))) {
                    return new AccessDecision.Allow();
                }
                return new AccessDecision.Deny("wrong combo");
            };

            Authorization auth = new Authorization(policy,
                new Attributes(Map.of(AttributeKeys.SUBJECT_USER_ID, "alice")));

            AccessDecision result = auth.evaluate(
                new Attributes(Map.of(AttributeKeys.ACTION_NAME, "delete")));
            assertInstanceOf(AccessDecision.Allow.class, result);
        }

        @Test
        void evaluate_deny_when_subject_mismatch() {
            AccessPolicy policy = attrs ->
                "admin".equals(attrs.getString(AttributeKeys.SUBJECT_USER_ID))
                    ? new AccessDecision.Allow()
                    : new AccessDecision.Deny("not admin");

            Authorization auth = new Authorization(policy,
                new Attributes(Map.of(AttributeKeys.SUBJECT_USER_ID, "bob")));

            assertInstanceOf(AccessDecision.Deny.class, auth.evaluate(Attributes.empty()));
        }

        @Test
        void delegated_merges_grant_attributes() {
            AccessPolicy policy = attrs -> {
                if (attrs.hasKey(AttributeKeys.GRANT_EXPIRES_AT)) {
                    return new AccessDecision.Allow();
                }
                return new AccessDecision.Deny("no grant");
            };

            Authorization auth = new Authorization(policy, Attributes.empty());
            DelegationGrant grant = new DelegationGrant("g1", Attributes.empty(),
                Instant.now(), Instant.now().plusSeconds(3600));

            Authorization delegated = auth.delegated(grant);
            assertInstanceOf(AccessDecision.Allow.class, delegated.evaluate(Attributes.empty()));
        }

        @Test
        void scoped_adds_entitlements() {
            AccessPolicy policy = attrs -> {
                @SuppressWarnings("unchecked")
                Set<String> allowed = (Set<String>) attrs.get(AttributeKeys.GRANT_SCOPE_ACTIONS);
                if (allowed != null && allowed.contains("read")) {
                    return new AccessDecision.Allow();
                }
                return new AccessDecision.Deny("no scope");
            };

            Authorization auth = new Authorization(policy, Attributes.empty());
            Authorization scoped = auth.scoped(Set.of("read", "list"));

            assertInstanceOf(AccessDecision.Allow.class, scoped.evaluate(Attributes.empty()));
        }

        @Test
        void policy_accessor() {
            AccessPolicy policy = _ -> new AccessDecision.Allow();
            Authorization auth = new Authorization(policy, Attributes.empty());
            assertSame(policy, auth.policy());
        }

        @Test
        void subject_attributes_accessor() {
            Attributes subject = new Attributes(Map.of("subject.user_id", "alice"));
            Authorization auth = new Authorization(_ -> new AccessDecision.Allow(), subject);
            assertEquals(subject, auth.subjectAttributes());
        }
    }

    // =====================
    // CompositePolicy
    // =====================

    @Nested
    class CompositePolicyTests {

        @Test
        void all_allow_results_in_allow() {
            CompositePolicy policy = new CompositePolicy(
                ExamplePolicies.allowAll(),
                ExamplePolicies.allowAll()
            );
            assertInstanceOf(AccessDecision.Allow.class, policy.evaluate(Attributes.empty()));
        }

        @Test
        void first_deny_wins() {
            CompositePolicy policy = new CompositePolicy(
                ExamplePolicies.allowAll(),
                ExamplePolicies.denyAll(),
                ExamplePolicies.allowAll()
            );
            AccessDecision result = policy.evaluate(Attributes.empty());
            assertInstanceOf(AccessDecision.Deny.class, result);
        }

        @Test
        void empty_composite_allows() {
            CompositePolicy policy = new CompositePolicy(List.of());
            assertInstanceOf(AccessDecision.Allow.class, policy.evaluate(Attributes.empty()));
        }

        @Test
        void single_policy_delegates() {
            CompositePolicy policy = new CompositePolicy(ExamplePolicies.denyAll());
            assertInstanceOf(AccessDecision.Deny.class, policy.evaluate(Attributes.empty()));
        }
    }

    // =====================
    // DelegationGrant
    // =====================

    @Nested
    class DelegationGrantTests {

        @Test
        void not_expired_when_no_expiry() {
            DelegationGrant grant = new DelegationGrant("g1", Attributes.empty(),
                Instant.now(), null);
            assertFalse(grant.isExpired());
        }

        @Test
        void not_expired_when_future_expiry() {
            DelegationGrant grant = new DelegationGrant("g1", Attributes.empty(),
                Instant.now(), Instant.now().plusSeconds(3600));
            assertFalse(grant.isExpired());
        }

        @Test
        void expired_when_past_expiry() {
            DelegationGrant grant = new DelegationGrant("g1", Attributes.empty(),
                Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
            assertTrue(grant.isExpired());
        }

        @Test
        void toAttributes_includes_grant_keys() {
            Attributes entitlements = Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("read"))
                .build();
            Instant expiry = Instant.now().plusSeconds(3600);
            DelegationGrant grant = new DelegationGrant("g1", entitlements,
                Instant.now(), expiry);

            Attributes attrs = grant.toAttributes();
            assertEquals(Set.of("read"), attrs.get(AttributeKeys.GRANT_SCOPE_ACTIONS));
            assertEquals(expiry, attrs.get(AttributeKeys.GRANT_EXPIRES_AT));
            assertEquals(false, attrs.get(AttributeKeys.GRANT_REVOKED));
        }

        @Test
        void requires_non_null_grantId() {
            assertThrows(NullPointerException.class,
                () -> new DelegationGrant(null, Attributes.empty(), Instant.now(), null));
        }

        @Test
        void requires_non_null_entitlements() {
            assertThrows(NullPointerException.class,
                () -> new DelegationGrant("g1", null, Instant.now(), null));
        }

        @Test
        void requires_non_null_createdAt() {
            assertThrows(NullPointerException.class,
                () -> new DelegationGrant("g1", Attributes.empty(), null, null));
        }
    }

    // =====================
    // GrantConstraints
    // =====================

    @Nested
    class GrantConstraintsTests {

        @Test
        void no_grant_attributes_passes() {
            assertNull(GrantConstraints.check(Attributes.empty()));
        }

        @Test
        void revoked_grant_fails() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_REVOKED, true)
                .build();
            assertEquals("Grant has been revoked", GrantConstraints.check(attrs));
        }

        @Test
        void expired_grant_fails() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_EXPIRES_AT, Instant.now().minusSeconds(3600))
                .build();
            assertEquals("Grant expired", GrantConstraints.check(attrs));
        }

        @Test
        void action_outside_scope_fails() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("read", "list"))
                .put(AttributeKeys.ACTION_NAME, "delete")
                .build();
            String result = GrantConstraints.check(attrs);
            assertNotNull(result);
            assertTrue(result.contains("delete"));
        }

        @Test
        void action_within_scope_passes() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_ACTIONS, Set.of("read", "list"))
                .put(AttributeKeys.ACTION_NAME, "read")
                .build();
            assertNull(GrantConstraints.check(attrs));
        }

        @Test
        void control_mode_outside_scope_fails() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_CONTROL_MODES, Set.of("supervised"))
                .put(AttributeKeys.CONTROL_MODE, "autonomous")
                .build();
            String result = GrantConstraints.check(attrs);
            assertNotNull(result);
            assertTrue(result.contains("autonomous"));
        }

        @Test
        void control_mode_within_scope_passes() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_SCOPE_CONTROL_MODES, Set.of("supervised", "autonomous"))
                .put(AttributeKeys.CONTROL_MODE, "supervised")
                .build();
            assertNull(GrantConstraints.check(attrs));
        }
    }

    // =====================
    // ExamplePolicies
    // =====================

    @Nested
    class ExamplePoliciesTests {

        @Test
        void allowAll_allows() {
            assertInstanceOf(AccessDecision.Allow.class,
                ExamplePolicies.allowAll().evaluate(Attributes.empty()));
        }

        @Test
        void denyAll_denies() {
            assertInstanceOf(AccessDecision.Deny.class,
                ExamplePolicies.denyAll().evaluate(Attributes.empty()));
        }

        @Test
        void requireAuthenticated_denies_unauthenticated() {
            assertInstanceOf(AccessDecision.Deny.class,
                ExamplePolicies.requireAuthenticated().evaluate(Attributes.empty()));
        }

        @Test
        void requireAuthenticated_allows_authenticated() {
            Attributes attrs = new Attributes(Map.of(AttributeKeys.SUBJECT_USER_ID, "alice"));
            assertInstanceOf(AccessDecision.Allow.class,
                ExamplePolicies.requireAuthenticated().evaluate(attrs));
        }

        @Test
        void readOnly_allows_read_actions() {
            Attributes attrs = new Attributes(Map.of(AttributeKeys.ACTION_TYPE, "read"));
            assertInstanceOf(AccessDecision.Allow.class,
                ExamplePolicies.readOnly().evaluate(attrs));
        }

        @Test
        void readOnly_denies_write_actions() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.ACTION_TYPE, "write")
                .put(AttributeKeys.ACTION_NAME, "delete")
                .build();
            assertInstanceOf(AccessDecision.Deny.class,
                ExamplePolicies.readOnly().evaluate(attrs));
        }

        @Test
        void readOnly_allows_navigate() {
            Attributes attrs = new Attributes(Map.of(AttributeKeys.ACTION_NAME, "navigate"));
            assertInstanceOf(AccessDecision.Allow.class,
                ExamplePolicies.readOnly().evaluate(attrs));
        }

        @Test
        void grantConstraints_passes_without_grant() {
            assertInstanceOf(AccessDecision.Allow.class,
                ExamplePolicies.grantConstraints().evaluate(Attributes.empty()));
        }

        @Test
        void grantConstraints_denies_revoked() {
            Attributes attrs = Attributes.builder()
                .put(AttributeKeys.GRANT_REVOKED, true)
                .build();
            assertInstanceOf(AccessDecision.Deny.class,
                ExamplePolicies.grantConstraints().evaluate(attrs));
        }
    }
}
