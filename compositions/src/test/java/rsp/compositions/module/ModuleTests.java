package rsp.compositions.module;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.CreateViewContract;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;
import rsp.compositions.schema.DataSchema;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Module interface default methods and slot-based resolution.
 */
public class ModuleTests {

    @Nested
    class SlotBasedResolutionTests {

        @Test
        void placementsForSlot_returns_matching_placements() {
            final rsp.compositions.module.Module module = new ModuleWithMixedSlots();

            final List<ViewPlacement> primaryPlacements = module.placementsForSlot(Slot.PRIMARY);
            final List<ViewPlacement> overlayPlacements = module.placementsForSlot(Slot.OVERLAY);

            assertEquals(1, primaryPlacements.size());
            assertEquals(2, overlayPlacements.size());
        }

        @Test
        void placementsForSlot_returns_empty_when_no_matches() {
            final rsp.compositions.module.Module module = new ModuleWithPrimaryOnly();

            final List<ViewPlacement> overlayPlacements = module.placementsForSlot(Slot.OVERLAY);

            assertTrue(overlayPlacements.isEmpty());
        }

        @Test
        void placementFor_finds_contract_class() {
            final rsp.compositions.module.Module module = new ModuleWithMixedSlots();

            final ViewPlacement placement = module.placementFor(TestListContract.class);

            assertNotNull(placement);
            assertEquals(TestListContract.class, placement.contractClass());
            assertEquals(Slot.PRIMARY, placement.slot());
        }

        @Test
        void placementFor_returns_null_when_not_found() {
            final rsp.compositions.module.Module module = new ModuleWithPrimaryOnly();

            final ViewPlacement placement = module.placementFor(TestEditContract.class);

            assertNull(placement);
        }
    }

    @Nested
    class ModuleInterfaceTests {

        @Test
        void views_returns_list() {
            final rsp.compositions.module.Module module = new TestModule();

            final List<ViewPlacement> views = module.views();

            assertNotNull(views);
        }

        @Test
        void empty_module_has_no_views() {
            final rsp.compositions.module.Module module = new TestModule();

            assertTrue(module.views().isEmpty());
        }
    }

    // Test fixtures

    static class TestModule implements Module {
        @Override
        public List<ViewPlacement> views() {
            return List.of();
        }
    }

    static class ModuleWithPrimaryOnly extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new)
            );
        }
    }

    static class ModuleWithMixedSlots extends TestModule {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new),
                    new ViewPlacement(Slot.OVERLAY, TestCreateContract.class, TestCreateContract::new),
                    new ViewPlacement(Slot.OVERLAY, TestEditContract.class, TestEditContract::new)
            );
        }
    }

    // Test contract classes

    static class TestViewContract extends ViewContract {
        TestViewContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        public rsp.component.ComponentContext enrichContext(rsp.component.ComponentContext context) {
            return context; // Test fixture - no enrichment needed
        }
    }

    static class TestListContract extends ListViewContract<Object> {
        TestListContract(final Lookup lookup) {
            super(lookup);
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
    }

    record TestEntity(String id, String name) {}

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

    static class TestEditContract extends EditViewContract<TestEntity> {
        TestEditContract(final Lookup lookup) {
            super(lookup);
        }

        @Override
        protected String resolveId() {
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
