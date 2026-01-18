package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.component.TestLookup;
import rsp.component.ContextKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ContextKeys registry and key behavior.
 */
public class ContextKeysTests {

    @Nested
    class KeyUniquenessTests {

        @Test
        void class_key_and_string_key_with_same_name_are_distinct() {
            final TestLookup lookup = new TestLookup()
                    .withData(String.class, "class-based-value")
                    .withData(new ContextKey.StringKey<>("java.lang.String", String.class), "string-based-value");

            assertEquals("class-based-value", lookup.get(String.class));
            assertEquals("string-based-value", lookup.get(new ContextKey.StringKey<>("java.lang.String", String.class)));
        }

        @Test
        void dynamic_key_extensions_are_distinct() {
            final ContextKey.DynamicKey<String> base = ContextKeys.URL_QUERY;

            final TestLookup lookup = new TestLookup()
                    .withData(base.with("p"), "1")
                    .withData(base.with("sort"), "asc")
                    .withData(base.with("filter"), "active");

            assertEquals("1", lookup.get(base.with("p")));
            assertEquals("asc", lookup.get(base.with("sort")));
            assertEquals("active", lookup.get(base.with("filter")));
        }

        @Test
        void different_dynamic_keys_with_same_extension_are_distinct() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.URL_QUERY.with("id"), "query-id")
                    .withData(ContextKeys.URL_PATH.with("id"), "path-id");

            assertEquals("query-id", lookup.get(ContextKeys.URL_QUERY.with("id")));
            assertEquals("path-id", lookup.get(ContextKeys.URL_PATH.with("id")));
        }
    }

    @Nested
    class DynamicKeyBuilderTests {

        @Test
        void with_appends_extension_with_dot() {
            final ContextKey.DynamicKey<String> base = new ContextKey.DynamicKey<>("url.query", String.class);

            final ContextKey.DynamicKey<String> extended = base.with("page");

            assertEquals("url.query.page", extended.baseKey());
        }

        @Test
        void with_preserves_type() {
            final ContextKey.DynamicKey<String> base = new ContextKey.DynamicKey<>("url.query", String.class);

            final ContextKey.DynamicKey<String> extended = base.with("page");

            assertEquals(String.class, extended.type());
        }

        @Test
        void with_can_be_chained() {
            final ContextKey.DynamicKey<String> base = new ContextKey.DynamicKey<>("config", String.class);

            final ContextKey.DynamicKey<String> extended = base.with("database").with("host");

            assertEquals("config.database.host", extended.baseKey());
        }

        @Test
        void url_query_base_key_is_correct() {
            assertEquals("url.query", ContextKeys.URL_QUERY.baseKey());
            assertEquals(String.class, ContextKeys.URL_QUERY.type());
        }

        @Test
        void url_path_base_key_is_correct() {
            assertEquals("url.path", ContextKeys.URL_PATH.baseKey());
            assertEquals(String.class, ContextKeys.URL_PATH.type());
        }
    }

    @Nested
    class TypeConsistencyTests {

        @Test
        void router_key_has_correct_type() {
            assertEquals(Router.class, ContextKeys.ROUTER.type());
            assertEquals(Router.class, ContextKeys.ROUTER.clazz());
        }

        @Test
        void edit_mode_key_has_correct_type() {
            assertEquals(EditMode.class, ContextKeys.EDIT_MODE.type());
        }

        @Test
        void list_schema_key_has_correct_type() {
            assertEquals(DataSchema.class, ContextKeys.LIST_SCHEMA.type());
        }

        @Test
        void route_pattern_key_has_correct_type() {
            assertEquals(String.class, ContextKeys.ROUTE_PATTERN.type());
        }

        @Test
        void list_page_key_has_correct_type() {
            assertEquals(Integer.class, ContextKeys.LIST_PAGE.type());
        }

        @Test
        void auth_roles_key_has_correct_type() {
            assertEquals(String[].class, ContextKeys.AUTH_ROLES.type());
        }
    }

    @Nested
    class ContextIntegrationTests {

        @Test
        void can_store_and_retrieve_router() {
            final Router router = new Router().route("/posts", TestContract.class);
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.ROUTER, router);

            assertSame(router, lookup.get(ContextKeys.ROUTER));
        }

        @Test
        void can_store_and_retrieve_edit_mode() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.EDIT_MODE, EditMode.MODAL);

            assertEquals(EditMode.MODAL, lookup.get(ContextKeys.EDIT_MODE));
        }

        @Test
        void can_store_and_retrieve_list_schema() {
            final DataSchema schema = DataSchema.fromRecordClass(TestRecord.class);
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.LIST_SCHEMA, schema);

            assertSame(schema, lookup.get(ContextKeys.LIST_SCHEMA));
        }

        @Test
        void can_store_and_retrieve_route_pattern() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.ROUTE_PATTERN, "/posts/:id");

            assertEquals("/posts/:id", lookup.get(ContextKeys.ROUTE_PATTERN));
        }

        @Test
        void can_store_and_retrieve_list_page() {
            final TestLookup lookup = new TestLookup()
                    .withData(ContextKeys.LIST_PAGE, 5);

            assertEquals(5, lookup.get(ContextKeys.LIST_PAGE));
        }
    }

    // Test fixtures
    record TestRecord(String id, String name) {}

    static class TestContract extends ViewContract {
        TestContract(final Lookup lookup) {
            super(lookup);
        }
    }
}
