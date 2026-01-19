package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.component.TestLookup;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Router path matching logic.
 */
public class RouterTests {

    // Minimal test contract for testing Router
    static class TestContract extends ViewContract {
        protected TestContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
            return context; // Test fixture - no enrichment needed
        }
    }

    static class AnotherTestContract extends ViewContract {
        protected AnotherTestContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
            return context; // Test fixture - no enrichment needed
        }
    }

    @Nested
    class ExactRouteMatchingTests {

        @Test
        void exact_route_matches_exact_path() {
            final Router router = new Router()
                    .route("/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts");

            assertTrue(match.isPresent());
            assertEquals(TestContract.class, match.get().contractClass());
            assertEquals("/posts", match.get().pattern());
        }

        @Test
        void exact_route_does_not_match_different_path() {
            final Router router = new Router()
                    .route("/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/users");

            assertFalse(match.isPresent());
        }

        @Test
        void exact_route_does_not_match_path_with_extra_segments() {
            final Router router = new Router()
                    .route("/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts/123");

            assertFalse(match.isPresent());
        }

        @Test
        void exact_route_does_not_match_path_with_fewer_segments() {
            final Router router = new Router()
                    .route("/admin/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/admin");

            assertFalse(match.isPresent());
        }
    }

    @Nested
    class ParameterizedRouteMatchingTests {

        @Test
        void param_route_matches_any_value_in_param_position() {
            final Router router = new Router()
                    .route("/posts/:id", TestContract.class);

            assertTrue(router.match("/posts/123").isPresent());
            assertTrue(router.match("/posts/abc").isPresent());
            assertTrue(router.match("/posts/hello-world").isPresent());
        }

        @Test
        void param_route_does_not_match_wrong_segment_count() {
            final Router router = new Router()
                    .route("/posts/:id", TestContract.class);

            assertFalse(router.match("/posts").isPresent());
            assertFalse(router.match("/posts/123/comments").isPresent());
        }

        @Test
        void param_route_matches_multiple_params() {
            final Router router = new Router()
                    .route("/posts/:postId/comments/:commentId", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts/42/comments/7");

            assertTrue(match.isPresent());
            assertEquals("/posts/:postId/comments/:commentId", match.get().pattern());
        }

        @Test
        void param_route_requires_exact_non_param_segments() {
            final Router router = new Router()
                    .route("/posts/:id", TestContract.class);

            assertFalse(router.match("/users/123").isPresent());
        }
    }

    @Nested
    class RouteOrderTests {

        @Test
        void duplicate_routes_last_registration_wins() {
            // Router uses a Map internally, so duplicate paths overwrite
            final Router router = new Router()
                    .route("/posts", TestContract.class)
                    .route("/posts", AnotherTestContract.class);  // Same path, different contract

            final Optional<Router.RouteMatch> match = router.match("/posts");

            assertTrue(match.isPresent());
            assertEquals(AnotherTestContract.class, match.get().contractClass());  // Last registered wins
        }

        @Test
        void exact_route_before_param_route_matches_exact() {
            // Critical: "/posts/new" must be registered before "/posts/:id"
            final Router router = new Router()
                    .route("/posts/new", TestContract.class)
                    .route("/posts/:id", AnotherTestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts/new");

            assertTrue(match.isPresent());
            assertEquals(TestContract.class, match.get().contractClass());
            assertEquals("/posts/new", match.get().pattern());
        }

        @Test
        void param_route_before_exact_route_matches_param() {
            // Wrong order: param route registered first will match "new" as an ID
            final Router router = new Router()
                    .route("/posts/:id", AnotherTestContract.class)
                    .route("/posts/new", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts/new");

            assertTrue(match.isPresent());
            // Param route wins because it was registered first
            assertEquals(AnotherTestContract.class, match.get().contractClass());
            assertEquals("/posts/:id", match.get().pattern());
        }
    }

    @Nested
    class QueryStringHandlingTests {

        @Test
        void match_strips_query_params() {
            final Router router = new Router()
                    .route("/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts?page=3&sort=asc");

            assertTrue(match.isPresent());
            assertEquals(TestContract.class, match.get().contractClass());
        }

        @Test
        void match_strips_query_params_from_param_route() {
            final Router router = new Router()
                    .route("/posts/:id", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts/123?edit=true");

            assertTrue(match.isPresent());
        }

        @Test
        void match_handles_empty_query_string() {
            final Router router = new Router()
                    .route("/posts", TestContract.class);

            final Optional<Router.RouteMatch> match = router.match("/posts?");

            assertTrue(match.isPresent());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void empty_router_matches_nothing() {
            final Router router = new Router();

            assertFalse(router.match("/posts").isPresent());
        }

        @Test
        void root_path_can_be_matched() {
            final Router router = new Router()
                    .route("/", TestContract.class);

            assertTrue(router.match("/").isPresent());
        }

        @Test
        void multiple_routes_correctly_distinguished() {
            final Router router = new Router()
                    .route("/posts", TestContract.class)
                    .route("/users", AnotherTestContract.class);

            assertEquals(TestContract.class, router.match("/posts").get().contractClass());
            assertEquals(AnotherTestContract.class, router.match("/users").get().contractClass());
        }
    }
}
