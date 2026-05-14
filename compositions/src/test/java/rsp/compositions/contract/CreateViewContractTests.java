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
        public String title() {
            return "TestEntity";
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

    @Nested
    class AgentActionsTests {

        @Test
        void agent_actions_include_set_field() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            final java.util.List<ContractAction> actions = contract.agentActions();
            final ContractAction setField = actions.stream()
                    .filter(a -> a.action().equals("set_field"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "set_field action must be declared on FormViewContract"));

            assertEquals(FormViewContract.FORM_FIELD_SET, setField.eventKey());
        }

        @Test
        void set_field_payload_schema_has_name_and_value_properties() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            final ContractAction setField = contract.agentActions().stream()
                    .filter(a -> a.action().equals("set_field"))
                    .findFirst().orElseThrow();

            assertInstanceOf(PayloadSchema.ObjectValue.class, setField.schema());
            final PayloadSchema.ObjectValue obj = (PayloadSchema.ObjectValue) setField.schema();
            assertEquals(2, obj.properties().size());
            assertTrue(obj.properties().stream().anyMatch(p -> p.name().equals("name") && p.required()),
                    "ObjectValue must declare required 'name' property");
            assertTrue(obj.properties().stream().anyMatch(p -> p.name().equals("value") && p.required()),
                    "ObjectValue must declare required 'value' property");
        }

        @Test
        void save_and_cancel_declared_as_scene_change() {
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            final java.util.Map<String, DispatchEffect> effects = contract.agentActions().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            ContractAction::action,
                            ContractAction::effect));

            assertEquals(DispatchEffect.SCENE_CHANGE, effects.get("save"),
                    "save closes the form overlay — must be declared SCENE_CHANGE");
            assertEquals(DispatchEffect.SCENE_CHANGE, effects.get("cancel"),
                    "cancel closes the form overlay — must be declared SCENE_CHANGE");
            assertEquals(DispatchEffect.NONE, effects.get("set_field"),
                    "set_field only updates form state — must be declared NONE");
        }

        @Test
        void save_action_still_available_for_human_dispatch() {
            // The agent prompt instructs the model not to call save, but the
            // action remains declared so human form submission still works.
            final Lookup lookup = lookupWithRoutePattern("/posts/new");
            final TestCreateContract contract = new TestCreateContract(lookup);

            assertTrue(contract.agentActions().stream()
                    .anyMatch(a -> a.action().equals("save")),
                    "save action must remain declared for the form submit path");
        }
    }
}
