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
    class PlacementLookupTests {

        @Test
        void placementFor_finds_contract_class() {
            final Composition composition = createCompositionWithMultipleContracts();

            final ViewPlacement placement = composition.placementFor(TestListContract.class);

            assertNotNull(placement);
            assertEquals(TestListContract.class, placement.contractClass());
        }

        @Test
        void placementFor_returns_null_when_not_found() {
            final Composition composition = createSimpleComposition();

            final ViewPlacement placement = composition.placementFor(TestEditContract.class);

            assertNull(placement);
        }

        @Test
        void views_returns_all_placements() {
            final Composition composition = createCompositionWithMultipleContracts();

            assertEquals(3, composition.views().size());
        }
    }

    @Nested
    class CompositionConstructorTests {

        @Test
        void composition_stores_router() {
            final Router router = new Router().route("/test", TestListContract.class);
            final ViewsPlacements placements = new ViewsPlacements()
                    .place(TestListContract.class, TestListContract::new);

            final Composition composition = new Composition(router, placements);

            assertSame(router, composition.router());
        }

        @Test
        void views_returns_immutable_list() {
            final Composition composition = createSimpleComposition();

            final List<ViewPlacement> views = composition.views();

            assertThrows(UnsupportedOperationException.class, () -> views.add(null));
        }

        @Test
        void constructor_rejects_null_router() {
            final ViewsPlacements placements = new ViewsPlacements();

            assertThrows(NullPointerException.class, () -> new Composition(null, placements));
        }

        @Test
        void constructor_rejects_null_placements() {
            final Router router = new Router();

            assertThrows(NullPointerException.class, () -> new Composition(router, null));
        }
    }

    @Nested
    class RouterIntegrationTests {

        @Test
        void router_matches_routes() {
            final Router router = new Router()
                    .route("/items", TestListContract.class)
                    .route("/items/:id", TestEditContract.class);
            final ViewsPlacements placements = new ViewsPlacements()
                    .place(TestListContract.class, TestListContract::new)
                    .place(TestEditContract.class, TestEditContract::new);

            final Composition composition = new Composition(router, placements);

            assertTrue(composition.router().hasRoute(TestListContract.class));
            assertTrue(composition.router().hasRoute(TestEditContract.class));
        }
    }

    // Helper methods to create test compositions

    private Composition createSimpleComposition() {
        final Router router = new Router().route("/items", TestListContract.class);
        final ViewsPlacements placements = new ViewsPlacements()
                .place(TestListContract.class, TestListContract::new);
        return new Composition(router, placements);
    }

    private Composition createCompositionWithMultipleContracts() {
        final Router router = new Router()
                .route("/items", TestListContract.class)
                .route("/items/:id", TestEditContract.class);
        final ViewsPlacements placements = new ViewsPlacements()
                .place(TestListContract.class, TestListContract::new)
                .place(TestCreateContract.class, TestCreateContract::new)
                .place(TestEditContract.class, TestEditContract::new);
        return new Composition(router, placements);
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
