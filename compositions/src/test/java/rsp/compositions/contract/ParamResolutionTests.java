package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.compositions.application.TestLookup;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for QueryParam and PathParam resolution mechanics.
 */
public class ParamResolutionTests {

    @Nested
    class QueryParamTests {

        @Test
        void resolve_extracts_from_url_query_dynamic_key() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("search"), "test");
            final QueryParam<String> param = new QueryParam<>("search", String.class, null);

            final String result = param.resolve(lookup);

            assertEquals("test", result);
        }

        @Test
        void resolve_applies_integer_type_conversion() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("page"), "42");
            final QueryParam<Integer> param = new QueryParam<>("page", Integer.class, 1);

            final Integer result = param.resolve(lookup);

            assertEquals(42, result);
        }

        @Test
        void resolve_applies_long_type_conversion() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("id"), "9876543210");
            final QueryParam<Long> param = new QueryParam<>("id", Long.class, 0L);

            final Long result = param.resolve(lookup);

            assertEquals(9876543210L, result);
        }

        @Test
        void resolve_applies_boolean_type_conversion() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("enabled"), "true");
            final QueryParam<Boolean> param = new QueryParam<>("enabled", Boolean.class, false);

            final Boolean result = param.resolve(lookup);

            assertTrue(result);
        }

        @Test
        void resolve_uses_default_when_missing() {
            final TestLookup lookup = new TestLookup();
            final QueryParam<String> param = new QueryParam<>("missing", String.class, "default-value");

            final String result = param.resolve(lookup);

            assertEquals("default-value", result);
        }

        @Test
        void resolve_uses_default_when_null() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("nullable"), null);
            final QueryParam<String> param = new QueryParam<>("nullable", String.class, "fallback");

            final String result = param.resolve(lookup);

            assertEquals("fallback", result);
        }

        @Test
        void resolve_returns_null_default_when_missing_and_no_default() {
            final TestLookup lookup = new TestLookup();
            final QueryParam<String> param = new QueryParam<>("missing", String.class, null);

            final String result = param.resolve(lookup);

            assertNull(result);
        }

        @Test
        void resolve_string_value_for_string_param() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("name"), "John");
            final QueryParam<String> param = new QueryParam<>("name", String.class, null);

            final String result = param.resolve(lookup);

            assertEquals("John", result);
        }

        @Test
        void resolve_with_custom_converter() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("status"), "ACTIVE");
            final QueryParam<Status> param = new QueryParam<>(
                    "status",
                    Status.class,
                    Status::valueOf,
                    Status.INACTIVE
            );

            final Status result = param.resolve(lookup);

            assertEquals(Status.ACTIVE, result);
        }
    }

    @Nested
    class PathParamTests {

        @Test
        void resolve_extracts_from_url_path_dynamic_key() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("1"), "posts");
            final PathParam<String> param = new PathParam<>(1, String.class, null);

            final String result = param.resolve(lookup);

            assertEquals("posts", result);
        }

        @Test
        void resolve_applies_integer_type_conversion() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("2"), "123");
            final PathParam<Integer> param = new PathParam<>(2, Integer.class, 0);

            final Integer result = param.resolve(lookup);

            assertEquals(123, result);
        }

        @Test
        void resolve_applies_long_type_conversion() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("1"), "9999999999");
            final PathParam<Long> param = new PathParam<>(1, Long.class, 0L);

            final Long result = param.resolve(lookup);

            assertEquals(9999999999L, result);
        }

        @Test
        void resolve_uses_default_when_missing() {
            final TestLookup lookup = new TestLookup();
            final PathParam<String> param = new PathParam<>(5, String.class, "default");

            final String result = param.resolve(lookup);

            assertEquals("default", result);
        }

        @Test
        void resolve_returns_null_when_missing_and_no_default() {
            final TestLookup lookup = new TestLookup();
            final PathParam<String> param = new PathParam<>(1, String.class, null);

            final String result = param.resolve(lookup);

            assertNull(result);
        }

        @Test
        void resolve_string_path_segment() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("0"), "posts");
            final PathParam<String> param = new PathParam<>(0, String.class, null);

            final String result = param.resolve(lookup);

            assertEquals("posts", result);
        }

        @Test
        void resolve_with_custom_converter() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("1"), "PENDING");
            final PathParam<Status> param = new PathParam<>(
                    1,
                    Status.class,
                    Status::valueOf,
                    Status.INACTIVE
            );

            final Status result = param.resolve(lookup);

            assertEquals(Status.PENDING, result);
        }

        @Test
        void resolve_multiple_path_segments() {
            // Simulating URL: /posts/42/comments/7
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_PATH.with("0"), "posts")
                    .withData(ContextKeys.URL_PATH.with("1"), "42")
                    .withData(ContextKeys.URL_PATH.with("2"), "comments")
                    .withData(ContextKeys.URL_PATH.with("3"), "7");

            final PathParam<Integer> postId = new PathParam<>(1, Integer.class, 0);
            final PathParam<Integer> commentId = new PathParam<>(3, Integer.class, 0);

            assertEquals(42, postId.resolve(lookup));
            assertEquals(7, commentId.resolve(lookup));
        }
    }

    @Nested
    class DefaultValueTests {

        @Test
        void query_param_default_can_be_null() {
            final QueryParam<String> param = new QueryParam<>("test", String.class, null);
            final TestLookup lookup = new TestLookup();

            assertNull(param.resolve(lookup));
        }

        @Test
        void query_param_default_can_be_zero() {
            final QueryParam<Integer> param = new QueryParam<>("count", Integer.class, 0);
            final TestLookup lookup = new TestLookup();

            assertEquals(0, param.resolve(lookup));
        }

        @Test
        void query_param_default_can_be_empty_string() {
            final QueryParam<String> param = new QueryParam<>("filter", String.class, "");
            final TestLookup lookup = new TestLookup();

            assertEquals("", param.resolve(lookup));
        }

        @Test
        void path_param_default_can_be_null() {
            final PathParam<String> param = new PathParam<>(0, String.class, null);
            final TestLookup lookup = new TestLookup();

            assertNull(param.resolve(lookup));
        }

        @Test
        void path_param_default_can_be_negative() {
            final PathParam<Integer> param = new PathParam<>(0, Integer.class, -1);
            final TestLookup lookup = new TestLookup();

            assertEquals(-1, param.resolve(lookup));
        }
    }

    // Test enum for custom converter tests
    enum Status {
        ACTIVE, INACTIVE, PENDING
    }
}
