package rsp.compositions.composition;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.*;
import rsp.compositions.routing.Router;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Composition class methods.
 */
public class CompositionTests {

    @Nested
    class ContractLookupTests {

        @Test
        void contractFactory_finds_registered_class() {
            final Composition composition = createCompositionWithMultipleContracts();

            final var factory = composition.uiRegistry().contractFactory(TestListContract.class);

            assertNotNull(factory);
        }

        @Test
        void contractFactory_returns_null_when_not_found() {
            final Composition composition = createSimpleComposition();

            final var factory = composition.uiRegistry().contractFactory(TestEditContract.class);

            assertNull(factory);
        }

        @Test
        void contractClasses_returns_all_registered_classes() {
            final Composition composition = createCompositionWithMultipleContracts();

            assertEquals(3, composition.uiRegistry().contractClasses().size());
        }
    }

    @Nested
    class CompositionConstructorTests {

        @Test
        void composition_stores_router() {
            final Router router = new Router().route("/test", TestListContract.class);
            final UiRegistry uiRegistry = new UiRegistry()
                    .register(TestListContract.class, TestListContract::new, () -> null);

            final Composition composition = new Composition(router, uiRegistry);

            assertSame(router, composition.router());
        }

        @Test
        void contractClasses_returns_unmodifiable_set() {
            final Composition composition = createSimpleComposition();

            final var classes = composition.uiRegistry().contractClasses();

            assertThrows(UnsupportedOperationException.class, () -> classes.add(null));
        }

        @Test
        void constructor_rejects_null_router() {
            final UiRegistry uiRegistry = new UiRegistry();

            assertThrows(NullPointerException.class, () -> new Composition(null, uiRegistry));
        }

        @Test
        void constructor_rejects_null_uiRegistry() {
            final Router router = new Router();

            assertThrows(NullPointerException.class, () -> new Composition(router, (UiRegistry) null));
        }
    }

    @Nested
    class RouterIntegrationTests {

        @Test
        void router_matches_routes() {
            final Router router = new Router()
                    .route("/items", TestListContract.class)
                    .route("/items/:id", TestEditContract.class);
            final UiRegistry uiRegistry = new UiRegistry()
                    .register(TestListContract.class, TestListContract::new, () -> null)
                    .register(TestEditContract.class, TestEditContract::new, () -> null);

            final Composition composition = new Composition(router, uiRegistry);

            assertTrue(composition.router().hasRoute(TestListContract.class));
            assertTrue(composition.router().hasRoute(TestEditContract.class));
        }
    }

    // Helper methods to create test compositions

    private Composition createSimpleComposition() {
        final Router router = new Router().route("/items", TestListContract.class);
        final UiRegistry uiRegistry = new UiRegistry()
                .register(TestListContract.class, TestListContract::new, () -> null);
        return new Composition(router, uiRegistry);
    }

    private Composition createCompositionWithMultipleContracts() {
        final Router router = new Router()
                .route("/items", TestListContract.class)
                .route("/items/:id", TestEditContract.class);
        final UiRegistry uiRegistry = new UiRegistry()
                .register(TestListContract.class, TestListContract::new, () -> null)
                .register(TestCreateContract.class, TestCreateContract::new, () -> null)
                .register(TestEditContract.class, TestEditContract::new, () -> null);
        return new Composition(router, uiRegistry);
    }

    // Test contract classes

    static class TestListContract extends ListViewContract<Object> {
        TestListContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        protected QueryParam<Integer> pageQueryParam() {
            return null;
        }

        @Override
        public String title() {
            return "Test";
        }

        @Override
        public int page() {
            return 1;
        }

        @Override
        public String sort() {
            return "asc";
        }

        @Override
        public List<Object> items() {
            return List.of();
        }

        @Override
        protected Class<? extends ViewContract> createElementContract() {
            return null;
        }

        @Override
        protected Class<? extends ViewContract> editElementContract() {
            return null;
        }
    }

    record TestEntity(String id, String name) {}

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

    static class TestEditContract extends EditViewContract<TestEntity> {
        TestEditContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public String title() {
            return "TestEntity";
        }

        @Override
        protected String resolveIdFromPath() {
            return null;
        }

        @Override
        public TestEntity item() {
            return null;
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
}
