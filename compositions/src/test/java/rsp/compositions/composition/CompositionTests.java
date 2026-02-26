package rsp.compositions.composition;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.*;
import rsp.compositions.layout.DefaultLayout;
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

            final var factory = composition.contracts().contractFactory(TestListContract.class);

            assertNotNull(factory);
        }

        @Test
        void contractFactory_returns_null_when_not_found() {
            final Composition composition = createSimpleComposition();

            final var factory = composition.contracts().contractFactory(TestEditContract.class);

            assertNull(factory);
        }

        @Test
        void contractClasses_returns_all_registered_classes() {
            final Composition composition = createCompositionWithMultipleContracts();

            assertEquals(3, composition.contracts().contractClasses().size());
        }
    }

    @Nested
    class CompositionConstructorTests {

        @Test
        void composition_stores_router() {
            final Router router = new Router().route("/test", TestListContract.class);
            final Group group = new Group()
                    .bind(TestListContract.class, TestListContract::new, () -> null);

            final Composition composition = new Composition(router, new DefaultLayout(), group);

            assertSame(router, composition.router());
        }

        @Test
        void contractClasses_returns_unmodifiable_set() {
            final Composition composition = createSimpleComposition();

            final var classes = composition.contracts().contractClasses();

            assertThrows(UnsupportedOperationException.class, () -> classes.add(null));
        }

        @Test
        void constructor_rejects_null_router() {
            final Group group = new Group();

            assertThrows(NullPointerException.class, () -> new Composition(null, new DefaultLayout(), group));
        }

        @Test
        void constructor_rejects_no_groups() {
            final Router router = new Router();

            assertThrows(IllegalArgumentException.class, () -> new Composition(router, new DefaultLayout()));
        }
    }

    @Nested
    class RouterIntegrationTests {

        @Test
        void router_matches_routes() {
            final Router router = new Router()
                    .route("/items", TestListContract.class)
                    .route("/items/:id", TestEditContract.class);
            final Group group = new Group()
                    .bind(TestListContract.class, TestListContract::new, () -> null)
                    .bind(TestEditContract.class, TestEditContract::new, () -> null);

            final Composition composition = new Composition(router, new DefaultLayout(), group);

            assertTrue(composition.router().hasRoute(TestListContract.class));
            assertTrue(composition.router().hasRoute(TestEditContract.class));
        }
    }

    @Nested
    class GroupTests {

        @Test
        void nested_group_contractClasses_aggregates() {
            final Group group = new Group("Root")
                    .add(new Group("A")
                            .bind(TestListContract.class, TestListContract::new, () -> null))
                    .add(new Group("B")
                            .bind(TestCreateContract.class, TestCreateContract::new, () -> null));

            assertEquals(2, group.contractClasses().size());
            assertTrue(group.contractClasses().contains(TestListContract.class));
            assertTrue(group.contractClasses().contains(TestCreateContract.class));
        }

        @Test
        void nested_group_contractFactory_finds_in_children() {
            final Group group = new Group("Root")
                    .add(new Group("A")
                            .bind(TestListContract.class, TestListContract::new, () -> null));

            assertNotNull(group.contractFactory(TestListContract.class));
        }

        @Test
        void structureTree_reflects_group_hierarchy() {
            final Group group = new Group("Root")
                    .bind(TestListContract.class, TestListContract::new, () -> null)
                    .add(new Group("Child")
                            .bind(TestCreateContract.class, TestCreateContract::new, () -> null));

            StructureNode tree = group.structureTree();

            assertEquals("Root", tree.label());
            assertEquals(1, tree.contracts().size());
            assertEquals(1, tree.children().size());
            assertEquals("Child", tree.children().get(0).label());
            assertEquals(1, tree.children().get(0).contracts().size());
        }

        @Test
        void structureNode_contains_searches_subtree() {
            final Group group = new Group("Root")
                    .add(new Group("Child")
                            .bind(TestListContract.class, TestListContract::new, () -> null));

            StructureNode tree = group.structureTree();

            assertTrue(tree.contains(TestListContract.class));
            assertFalse(tree.contains(TestEditContract.class));
        }

        @Test
        void structureNode_labelFor_finds_owning_group() {
            final Group group = new Group("Root")
                    .add(new Group("Posts")
                            .bind(TestListContract.class, TestListContract::new, () -> null))
                    .add(new Group("Comments")
                            .bind(TestCreateContract.class, TestCreateContract::new, () -> null));

            StructureNode tree = group.structureTree();

            assertEquals("Posts", tree.labelFor(TestListContract.class));
            assertEquals("Comments", tree.labelFor(TestCreateContract.class));
            assertNull(tree.labelFor(TestEditContract.class));
        }

        @Test
        void composition_merges_multiple_groups() {
            final Router router = new Router().route("/items", TestListContract.class);
            final Group main = new Group("Main")
                    .bind(TestListContract.class, TestListContract::new, () -> null);
            final Group system = new Group()
                    .bind(TestCreateContract.class, TestCreateContract::new, () -> null);

            final Composition composition = new Composition(router, new DefaultLayout(), main, system);

            assertEquals(2, composition.contracts().contractClasses().size());
            assertNotNull(composition.contracts().contractFactory(TestListContract.class));
            assertNotNull(composition.contracts().contractFactory(TestCreateContract.class));
        }
    }

    // Helper methods to create test compositions

    private Composition createSimpleComposition() {
        final Router router = new Router().route("/items", TestListContract.class);
        final Group group = new Group()
                .bind(TestListContract.class, TestListContract::new, () -> null);
        return new Composition(router, new DefaultLayout(), group);
    }

    private Composition createCompositionWithMultipleContracts() {
        final Router router = new Router()
                .route("/items", TestListContract.class)
                .route("/items/:id", TestEditContract.class);
        final Group group = new Group()
                .bind(TestListContract.class, TestListContract::new, () -> null)
                .bind(TestCreateContract.class, TestCreateContract::new, () -> null)
                .bind(TestEditContract.class, TestEditContract::new, () -> null);
        return new Composition(router, new DefaultLayout(), group);
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
