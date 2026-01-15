package rsp.compositions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.Subscriber;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.page.events.Command;

import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EditViewContract create mode detection and list route derivation.
 */
public class EditViewContractTests {

    // Test entity
    record TestEntity(String id, String name) {}

    // No-op Subscriber for tests
    static class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType,
                                             Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}
    }

    // No-op CommandsEnqueue for tests
    static class NoOpCommandsEnqueue implements CommandsEnqueue {
        @Override
        public void offer(Command command) {}
    }

    // Minimal test contract with configurable ID resolution
    static class TestEditContract extends EditViewContract<TestEntity> {
        private final String resolvedId;

        TestEditContract(final ComponentContext context, final String resolvedId) {
            super(context);
            this.resolvedId = resolvedId;
        }

        @Override
        protected String resolveId() {
            return resolvedId;
        }

        @Override
        public TestEntity item() {
            return isCreateMode() ? null : new TestEntity(resolvedId, "Test");
        }

        @Override
        public ListSchema schema() {
            return ListSchema.fromRecordClass(TestEntity.class);
        }

        @Override
        public boolean save(final Map<String, Object> fieldValues) {
            return true;
        }
    }

    // Contract with custom create token
    static class CustomTokenContract extends TestEditContract {
        CustomTokenContract(final ComponentContext context, final String resolvedId) {
            super(context, resolvedId);
        }

        @Override
        protected String createToken() {
            return "_";  // Custom create token
        }
    }

    private ComponentContext contextWithRoutePattern(final String pattern) {
        return new ComponentContext()
                .with(ContextKeys.ROUTE_PATTERN, pattern)
                .with(CommandsEnqueue.class, new NoOpCommandsEnqueue())
                .with(Subscriber.class, new NoOpSubscriber());
    }

    @Nested
    class CreateModeDetectionTests {

        @Test
        void is_create_mode_true_when_id_is_null() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, null);

            assertTrue(contract.isCreateMode());
        }

        @Test
        void is_create_mode_true_when_id_equals_create_token() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "new");

            assertTrue(contract.isCreateMode());
        }

        @Test
        void is_create_mode_false_when_id_is_valid_value() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertFalse(contract.isCreateMode());
        }

        @Test
        void is_create_mode_false_when_id_is_empty_string() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "");

            assertFalse(contract.isCreateMode());  // Empty string is not null
        }
    }

    @Nested
    class CreateTokenTests {

        @Test
        void default_create_token_is_new() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "ignored");

            assertEquals("new", contract.createToken());
        }

        @Test
        void custom_create_token_can_be_overridden() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final CustomTokenContract contract = new CustomTokenContract(context, "_");

            assertEquals("_", contract.createToken());
            assertTrue(contract.isCreateMode());  // "_" matches custom token
        }

        @Test
        void custom_create_token_default_token_no_longer_matches() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final CustomTokenContract contract = new CustomTokenContract(context, "new");

            assertFalse(contract.isCreateMode());  // "new" doesn't match "_"
        }
    }

    @Nested
    class ListRouteDerivationTests {

        @Test
        void list_route_strips_param_from_pattern() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_strips_create_token_from_pattern() {
            final ComponentContext context = contextWithRoutePattern("/posts/new");
            final TestEditContract contract = new TestEditContract(context, "new");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_preserves_nested_path() {
            final ComponentContext context = contextWithRoutePattern("/admin/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/admin/posts", contract.listRoute());
        }

        @Test
        void list_route_handles_multiple_params_strips_last() {
            final ComponentContext context = contextWithRoutePattern("/posts/:postId/comments/:id");
            final TestEditContract contract = new TestEditContract(context, "456");

            assertEquals("/posts/:postId/comments", contract.listRoute());
        }

        @Test
        void list_route_no_param_at_end_returns_full_pattern() {
            final ComponentContext context = contextWithRoutePattern("/posts/list");
            final TestEditContract contract = new TestEditContract(context, "ignored");

            assertEquals("/posts/list", contract.listRoute());
        }

        @Test
        void list_route_root_with_param_returns_pattern() {
            // Edge case: when pattern is just "/:id", there's no parent path to strip to
            // The implementation returns the full pattern in this case
            final ComponentContext context = contextWithRoutePattern("/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/:id", contract.listRoute());
        }

        @Test
        void list_route_throws_when_no_route_pattern() {
            // No route pattern, but still needs CommandsEnqueue and Subscriber
            final ComponentContext context = new ComponentContext()
                    .with(CommandsEnqueue.class, new NoOpCommandsEnqueue())
                    .with(Subscriber.class, new NoOpSubscriber());
            final TestEditContract contract = new TestEditContract(context, "123");

            assertThrows(IllegalStateException.class, contract::listRoute);
        }
    }

    @Nested
    class QueryParamRestorationTests {

        @Test
        void list_route_restores_from_p_query_param() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id")
                    .with(ContextKeys.URL_QUERY.with("fromP"), "3");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts?p=3", contract.listRoute());
        }

        @Test
        void list_route_restores_from_sort_query_param() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id")
                    .with(ContextKeys.URL_QUERY.with("fromSort"), "desc");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts?sort=desc", contract.listRoute());
        }

        @Test
        void list_route_restores_multiple_query_params() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id")
                    .with(ContextKeys.URL_QUERY.with("fromP"), "2")
                    .with(ContextKeys.URL_QUERY.with("fromSort"), "asc");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts?p=2&sort=asc", contract.listRoute());
        }

        @Test
        void list_route_ignores_empty_from_params() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id")
                    .with(ContextKeys.URL_QUERY.with("fromP"), "");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts", contract.listRoute());
        }

        @Test
        void list_route_no_from_params_no_query_string() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertEquals("/posts", contract.listRoute());
        }
    }

    @Nested
    class ItemMethodTests {

        @Test
        void item_returns_null_in_create_mode() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, null);

            assertNull(contract.item());
        }

        @Test
        void item_returns_entity_in_edit_mode() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            final TestEntity entity = contract.item();

            assertNotNull(entity);
            assertEquals("123", entity.id());
        }
    }

    @Nested
    class SchemaMethodTests {

        @Test
        void schema_returns_schema_from_record_class() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            final ListSchema schema = contract.schema();

            assertEquals(2, schema.columns().size());
            assertEquals("id", schema.columns().get(0).name());
            assertEquals("name", schema.columns().get(1).name());
        }
    }

    @Nested
    class DeleteMethodTests {

        @Test
        void delete_throws_illegal_state_in_create_mode_null_id() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, null);

            assertThrows(IllegalStateException.class, contract::delete);
        }

        @Test
        void delete_throws_illegal_state_in_create_mode_new_token() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "new");

            assertThrows(IllegalStateException.class, contract::delete);
        }

        @Test
        void delete_throws_unsupported_by_default_in_edit_mode() {
            final ComponentContext context = contextWithRoutePattern("/posts/:id");
            final TestEditContract contract = new TestEditContract(context, "123");

            assertThrows(UnsupportedOperationException.class, contract::delete);
        }
    }
}
