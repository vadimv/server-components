package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.application.TestLookup;
import rsp.compositions.schema.DataSchema;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EditViewContract list route derivation and edit-mode behavior.
 * <p>
 * For create mode tests, see {@link CreateViewContractTests}.
 */
public class EditViewContractTests {

    // Test entity
    record TestEntity(String id, String name) {}

    // Minimal test contract for editing
    static class TestEditContract extends EditViewContract<TestEntity> {
        private final String resolvedId;

        TestEditContract(final Lookup lookup, final String resolvedId) {
            super(lookup);
            this.resolvedId = resolvedId;
        }

        @Override
        public Object typeHint() {
            return TestEntity.class;
        }

        @Override
        public String title() {
            return "TestEntity";
        }

        @Override
        protected String resolveIdFromPath() {
            return resolvedId;
        }

        @Override
        public TestEntity item() {
            return new TestEntity(resolveId(), "Test");
        }

        @Override
        public DataSchema schema() {
            return DataSchema.fromRecordClass(TestEntity.class);
        }

        @Override
        public boolean save(final Map<String, Object> fieldValues) {
            return true;
        }
    }

    private TestLookup lookupWithRoutePattern(final String pattern) {
        return new TestLookup()
                .withData(ContextKeys.ROUTE_PATTERN, pattern);
    }

    @Nested
    class ListRouteDerivationTests {

        @Test
        void list_route_strips_param_from_pattern() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_strips_new_token_from_pattern() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestEditContract contract = new TestEditContract(lookup, "new");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_preserves_nested_path() {
            final Lookup lookup = lookupWithRoutePattern("/admin/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/admin/posts", contract.listRoute());
        }

        @Test
        void list_route_handles_multiple_params_strips_last() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:postId/comments/:id");
            final TestEditContract contract = new TestEditContract(lookup, "456");

            assertEquals("/posts/:postId/comments", contract.listRoute());
        }

        @Test
        void list_route_no_param_at_end_returns_full_pattern() {
            final Lookup lookup = lookupWithRoutePattern("/posts/list");
            final TestEditContract contract = new TestEditContract(lookup, "ignored");

            assertEquals("/posts/list", contract.listRoute());
        }

        @Test
        void list_route_root_with_param_returns_pattern() {
            // Edge case: when pattern is just "/:id", there's no parent path to strip to
            final Lookup lookup = lookupWithRoutePattern("/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/:id", contract.listRoute());
        }

        @Test
        void list_route_throws_when_no_route_pattern() {
            final Lookup lookup = new TestLookup();
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertThrows(IllegalStateException.class, contract::listRoute);
        }
    }

    @Nested
    class QueryParamRestorationTests {

        @Test
        void list_route_restores_from_p_query_param() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id")
                    .withData(ContextKeys.URL_QUERY.with("fromP"), "3");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts?p=3", contract.listRoute());
        }

        @Test
        void list_route_restores_from_sort_query_param() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id")
                    .withData(ContextKeys.URL_QUERY.with("fromSort"), "desc");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts?sort=desc", contract.listRoute());
        }

        @Test
        void list_route_restores_multiple_query_params() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id")
                    .withData(ContextKeys.URL_QUERY.with("fromP"), "2")
                    .withData(ContextKeys.URL_QUERY.with("fromSort"), "asc");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts?p=2&sort=asc", contract.listRoute());
        }

        @Test
        void list_route_ignores_empty_from_params() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id")
                    .withData(ContextKeys.URL_QUERY.with("fromP"), "");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_no_from_params_no_query_string() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertEquals("/posts", contract.listRoute());
        }
    }

    @Nested
    class ItemMethodTests {

        @Test
        void item_returns_entity() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            final TestEntity entity = contract.item();

            assertNotNull(entity);
            assertEquals("123", entity.id());
        }
    }

    @Nested
    class SchemaMethodTests {

        @Test
        void schema_returns_schema_from_record_class() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            final DataSchema schema = contract.schema();

            assertEquals(2, schema.columns().size());
            assertEquals("id", schema.columns().get(0).name());
            assertEquals("name", schema.columns().get(1).name());
        }
    }

    @Nested
    class DeleteMethodTests {

        @Test
        void delete_throws_unsupported_by_default() {
            final Lookup lookup = lookupWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(lookup, "123");

            assertThrows(UnsupportedOperationException.class, contract::delete);
        }
    }
}
