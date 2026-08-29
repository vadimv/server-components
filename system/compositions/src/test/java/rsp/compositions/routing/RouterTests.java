package rsp.compositions.routing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.block.Block;
import rsp.server.Path;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Router path matching logic.
 */
public class RouterTests {

    // Minimal test block for testing Router
    static class TestBlock extends Block<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }

        @Override
        public String title() {
            return "Test";
        }

    }

    static class AnotherTestBlock extends TestBlock {

        @Override
        public String title() {
            return "AnotherTest";
        }

    }

    @Nested
    class ExactRouteMatchingTests {

        @Test
        void exact_route_matches_exact_path() {
            final Router router = new Router()
                    .route("/posts", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts"));

            assertTrue(match.isPresent());
            assertEquals(TestBlock.class, match.get().blockClass());
            assertEquals("/posts", match.get().pattern());
        }

        @Test
        void exact_route_preserves_an_application_defined_block_key() {
            Object postsKey = new Object();
            Router router = new Router().route("/posts", postsKey);

            Router.RouteMatch match = router.match(Path.of("/posts")).orElseThrow();

            assertSame(postsKey, match.blockKey());
            assertTrue(router.hasRoute(postsKey));
            assertEquals("/posts", router.findRoutePattern(postsKey).orElseThrow());
            assertThrows(IllegalStateException.class, match::blockClass);
        }

        @Test
        void exact_route_does_not_match_different_path() {
            final Router router = new Router()
                    .route("/posts", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/users"));

            assertFalse(match.isPresent());
        }

        @Test
        void exact_route_does_not_match_path_with_extra_segments() {
            final Router router = new Router()
                    .route("/posts", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts/123"));

            assertFalse(match.isPresent());
        }

        @Test
        void exact_route_does_not_match_path_with_fewer_segments() {
            final Router router = new Router()
                    .route("/admin/posts", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/admin"));

            assertFalse(match.isPresent());
        }
    }

    @Nested
    class ParameterizedRouteMatchingTests {

        @Test
        void param_route_matches_any_value_in_param_position() {
            final Router router = new Router()
                    .route("/posts/:id", TestBlock.class);

            assertTrue(router.match(Path.of("/posts/123")).isPresent());
            assertTrue(router.match(Path.of("/posts/abc")).isPresent());
            assertTrue(router.match(Path.of("/posts/hello-world")).isPresent());
        }

        @Test
        void param_route_does_not_match_wrong_segment_count() {
            final Router router = new Router()
                    .route("/posts/:id", TestBlock.class);

            assertFalse(router.match(Path.of("/posts")).isPresent());
            assertFalse(router.match(Path.of("/posts/123/comments")).isPresent());
        }

        @Test
        void param_route_matches_multiple_params() {
            final Router router = new Router()
                    .route("/posts/:postId/comments/:commentId", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts/42/comments/7"));

            assertTrue(match.isPresent());
            assertEquals("/posts/:postId/comments/:commentId", match.get().pattern());
        }

        @Test
        void param_route_requires_exact_non_param_segments() {
            final Router router = new Router()
                    .route("/posts/:id", TestBlock.class);

            assertFalse(router.match(Path.of("/users/123")).isPresent());
        }
    }

    @Nested
    class RouteOrderTests {

        @Test
        void duplicate_routes_last_registration_wins() {
            // Router uses a Map internally, so duplicate paths overwrite
            final Router router = new Router()
                    .route("/posts", TestBlock.class)
                    .route("/posts", AnotherTestBlock.class);  // Same path, different block

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts"));

            assertTrue(match.isPresent());
            assertEquals(AnotherTestBlock.class, match.get().blockClass());  // Last registered wins
        }

        @Test
        void exact_route_before_param_route_matches_exact() {
            // Critical: "/posts/new" must be registered before "/posts/:id"
            final Router router = new Router()
                    .route("/posts/new", TestBlock.class)
                    .route("/posts/:id", AnotherTestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts/new"));

            assertTrue(match.isPresent());
            assertEquals(TestBlock.class, match.get().blockClass());
            assertEquals("/posts/new", match.get().pattern());
        }

        @Test
        void param_route_before_exact_route_matches_param() {
            // Wrong order: param route registered first will match "new" as an ID
            final Router router = new Router()
                    .route("/posts/:id", AnotherTestBlock.class)
                    .route("/posts/new", TestBlock.class);

            final Optional<Router.RouteMatch> match = router.match(Path.of("/posts/new"));

            assertTrue(match.isPresent());
            // Param route wins because it was registered first
            assertEquals(AnotherTestBlock.class, match.get().blockClass());
            assertEquals("/posts/:id", match.get().pattern());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void empty_router_matches_nothing() {
            final Router router = new Router();

            assertFalse(router.match(Path.of("/posts")).isPresent());
        }

        @Test
        void root_path_can_be_matched() {
            final Router router = new Router()
                    .route("/", TestBlock.class);

            assertTrue(router.match(Path.of("/")).isPresent());
        }

        @Test
        void multiple_routes_correctly_distinguished() {
            final Router router = new Router()
                    .route("/posts", TestBlock.class)
                    .route("/users", AnotherTestBlock.class);

            assertEquals(TestBlock.class, router.match(Path.of("/posts")).get().blockClass());
            assertEquals(AnotherTestBlock.class, router.match(Path.of("/users")).get().blockClass());
        }
    }
}
