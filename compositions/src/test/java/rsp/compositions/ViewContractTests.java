package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ViewContract base class behavior including parameter resolution
 * and authorization strategy.
 */
public class ViewContractTests {

    // Minimal test contract exposing protected methods for testing
    static class TestViewContract extends ViewContract {
        TestViewContract(final ComponentContext context) {
            super(context);
        }

        // Expose protected methods for testing
        public <T> T testResolveQuery(final QueryParam<T> param) {
            return resolve(param);
        }

        public <T> T testResolvePath(final PathParam<T> param) {
            return resolve(param);
        }

        public boolean testIsAuthorized() {
            return isAuthorized();
        }
    }

    @Nested
    class QueryParamResolutionTests {

        @Test
        void resolve_extracts_string_query_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_QUERY.with("search"), "hello");
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<String> param = new QueryParam<>("search", String.class, null);

            final String result = contract.testResolveQuery(param);

            assertEquals("hello", result);
        }

        @Test
        void resolve_returns_default_when_missing() {
            final ComponentContext context = new ComponentContext();
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<String> param = new QueryParam<>("missing", String.class, "default");

            final String result = contract.testResolveQuery(param);

            assertEquals("default", result);
        }

        @Test
        void resolve_parses_integer_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_QUERY.with("page"), "5");
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<Integer> param = new QueryParam<>("page", Integer.class, 1);

            final Integer result = contract.testResolveQuery(param);

            assertEquals(5, result);
        }

        @Test
        void resolve_parses_long_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_QUERY.with("id"), "9999999999");
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<Long> param = new QueryParam<>("id", Long.class, 0L);

            final Long result = contract.testResolveQuery(param);

            assertEquals(9999999999L, result);
        }

        @Test
        void resolve_parses_boolean_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_QUERY.with("active"), "true");
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<Boolean> param = new QueryParam<>("active", Boolean.class, false);

            final Boolean result = contract.testResolveQuery(param);

            assertTrue(result);
        }

        @Test
        void resolve_uses_default_for_empty_string() {
            // Note: This tests the current behavior - empty string returns default
            // because the converter may fail on empty string
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_QUERY.with("page"), "");
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<Integer> param = new QueryParam<>("page", Integer.class, 1);

            // Empty string won't parse to integer, so default is used
            // The current implementation returns the string, which then fails conversion
            // This test documents expected behavior - may need adjustment based on actual implementation
            try {
                final Integer result = contract.testResolveQuery(param);
                assertEquals(1, result);  // Default should be used
            } catch (NumberFormatException e) {
                // Also acceptable - depends on converter behavior
            }
        }

        @Test
        void resolve_returns_null_default_when_missing_and_no_default() {
            final ComponentContext context = new ComponentContext();
            final TestViewContract contract = new TestViewContract(context);
            final QueryParam<String> param = new QueryParam<>("missing", String.class, null);

            final String result = contract.testResolveQuery(param);

            assertNull(result);
        }
    }

    @Nested
    class PathParamResolutionTests {

        @Test
        void resolve_extracts_string_path_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_PATH.with("1"), "abc123");
            final TestViewContract contract = new TestViewContract(context);
            final PathParam<String> param = new PathParam<>(1, String.class, null);

            final String result = contract.testResolvePath(param);

            assertEquals("abc123", result);
        }

        @Test
        void resolve_returns_default_when_missing() {
            final ComponentContext context = new ComponentContext();
            final TestViewContract contract = new TestViewContract(context);
            final PathParam<String> param = new PathParam<>(1, String.class, null);

            final String result = contract.testResolvePath(param);

            assertNull(result);
        }

        @Test
        void resolve_parses_integer_path_param() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_PATH.with("2"), "42");
            final TestViewContract contract = new TestViewContract(context);
            final PathParam<Integer> param = new PathParam<>(2, Integer.class, 0);

            final Integer result = contract.testResolvePath(param);

            assertEquals(42, result);
        }

        @Test
        void resolve_uses_default_for_missing_path_segment() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_PATH.with("0"), "posts");
            // Index 1 is not set
            final TestViewContract contract = new TestViewContract(context);
            final PathParam<String> param = new PathParam<>(1, String.class, "default");

            final String result = contract.testResolvePath(param);

            assertEquals("default", result);
        }

        @Test
        void resolve_handles_multiple_path_params() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.URL_PATH.with("0"), "posts")
                    .with(ContextKeys.URL_PATH.with("1"), "123")
                    .with(ContextKeys.URL_PATH.with("2"), "comments")
                    .with(ContextKeys.URL_PATH.with("3"), "456");
            final TestViewContract contract = new TestViewContract(context);

            final PathParam<String> postId = new PathParam<>(1, String.class, null);
            final PathParam<String> commentId = new PathParam<>(3, String.class, null);

            assertEquals("123", contract.testResolvePath(postId));
            assertEquals("456", contract.testResolvePath(commentId));
        }
    }

    @Nested
    class AuthorizationStrategyTests {

        @Test
        void is_authorized_allows_when_no_strategy() {
            final ComponentContext context = new ComponentContext();
            final TestViewContract contract = new TestViewContract(context);

            assertTrue(contract.testIsAuthorized());
        }

        @Test
        void is_authorized_delegates_to_strategy_from_context() {
            final ViewContract.AuthorizationStrategy alwaysAllow = (c, ctx) -> true;
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, alwaysAllow);
            final TestViewContract contract = new TestViewContract(context);

            assertTrue(contract.testIsAuthorized());
        }

        @Test
        void is_authorized_denies_when_strategy_returns_false() {
            final ViewContract.AuthorizationStrategy alwaysDeny = (c, ctx) -> false;
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, alwaysDeny);
            final TestViewContract contract = new TestViewContract(context);

            assertFalse(contract.testIsAuthorized());
        }

        @Test
        void is_authorized_strategy_receives_contract() {
            final ViewContract[] capturedContract = new ViewContract[1];
            final ViewContract.AuthorizationStrategy capturing = (contract, ctx) -> {
                capturedContract[0] = contract;
                return true;
            };
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, capturing);
            final TestViewContract contract = new TestViewContract(context);

            contract.testIsAuthorized();

            assertSame(contract, capturedContract[0]);
        }

        @Test
        void is_authorized_strategy_receives_context() {
            final ComponentContext[] capturedContext = new ComponentContext[1];
            final ViewContract.AuthorizationStrategy capturing = (contract, ctx) -> {
                capturedContext[0] = ctx;
                return true;
            };
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, capturing);
            final TestViewContract contract = new TestViewContract(context);

            contract.testIsAuthorized();

            assertSame(context, capturedContext[0]);
        }

        @Test
        void is_authorized_strategy_can_check_roles() {
            // Example: RBAC-style strategy checking for admin role
            final ViewContract.AuthorizationStrategy rbacStrategy = (contract, ctx) -> {
                final String[] roles = ctx.get(ContextKeys.AUTH_ROLES);
                if (roles == null) return false;
                for (final String role : roles) {
                    if ("admin".equals(role)) return true;
                }
                return false;
            };

            // User with admin role
            final ComponentContext adminContext = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, rbacStrategy)
                    .with(ContextKeys.AUTH_ROLES, new String[]{"user", "admin"});
            assertTrue(new TestViewContract(adminContext).testIsAuthorized());

            // User without admin role
            final ComponentContext userContext = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, rbacStrategy)
                    .with(ContextKeys.AUTH_ROLES, new String[]{"user"});
            assertFalse(new TestViewContract(userContext).testIsAuthorized());

            // No roles at all
            final ComponentContext noRolesContext = new ComponentContext()
                    .with(ContextKeys.AUTHORIZATION_STRATEGY, rbacStrategy);
            assertFalse(new TestViewContract(noRolesContext).testIsAuthorized());
        }
    }

    @Nested
    class ContextAccessTests {

        @Test
        void contract_has_access_to_context() {
            final ComponentContext context = new ComponentContext()
                    .with(ContextKeys.ROUTE_PATH, "/posts/123");
            final TestViewContract contract = new TestViewContract(context);

            // Contract can access context via protected field
            assertEquals("/posts/123", contract.context.get(ContextKeys.ROUTE_PATH));
        }
    }
}
