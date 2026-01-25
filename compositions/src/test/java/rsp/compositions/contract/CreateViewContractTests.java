package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.application.TestLookup;
import rsp.compositions.schema.DataSchema;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CreateViewContract create-mode behavior.
 */
public class CreateViewContractTests {

    // Test entity
    record TestEntity(String id, String name) {}

    // Minimal test contract for creating
    static class TestCreateContract extends CreateViewContract<TestEntity> {

        TestCreateContract(final Lookup lookup) {
            super(lookup);
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
    class CreateModeTests {

        @Test
        void is_create_mode_always_true() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertTrue(contract.isCreateMode());
        }
    }

    @Nested
    class ListRouteDerivationTests {

        @Test
        void list_route_strips_new_token_from_pattern() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_strips_create_token_from_pattern() {
            final Lookup lookup = lookupWithRoutePattern("/posts/create");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_preserves_nested_path() {
            final Lookup lookup = lookupWithRoutePattern("/admin/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertEquals("/admin/posts", contract.listRoute());
        }

        @Test
        void list_route_restores_query_params() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new")
                    .withData(ContextKeys.URL_QUERY.with("fromP"), "3");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertEquals("/posts?p=3", contract.listRoute());
        }
    }

    @Nested
    class EnrichContextTests {

        @Test
        void enrichContext_sets_create_mode_true() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);
            final ComponentContext baseContext = new ComponentContext();

            final ComponentContext enriched = contract.enrichContext(baseContext);

            assertTrue(enriched.get(ContextKeys.EDIT_IS_CREATE_MODE));
        }

        @Test
        void enrichContext_sets_entity_null() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);
            final ComponentContext baseContext = new ComponentContext();

            final ComponentContext enriched = contract.enrichContext(baseContext);

            assertNull(enriched.get(ContextKeys.EDIT_ENTITY));
        }

        @Test
        void enrichContext_sets_schema() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);
            final ComponentContext baseContext = new ComponentContext();

            final ComponentContext enriched = contract.enrichContext(baseContext);

            assertNotNull(enriched.get(ContextKeys.EDIT_SCHEMA));
        }

        @Test
        void enrichContext_sets_list_route() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);
            final ComponentContext baseContext = new ComponentContext();

            final ComponentContext enriched = contract.enrichContext(baseContext);

            assertEquals("/posts", enriched.get(ContextKeys.EDIT_LIST_ROUTE));
        }
    }

    @Nested
    class SchemaMethodTests {

        @Test
        void schema_returns_schema_from_record_class() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            final DataSchema schema = contract.schema();

            assertEquals(2, schema.columns().size());
            assertEquals("id", schema.columns().get(0).name());
            assertEquals("name", schema.columns().get(1).name());
        }
    }
}
