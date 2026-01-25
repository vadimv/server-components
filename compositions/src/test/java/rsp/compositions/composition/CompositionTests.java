package rsp.compositions.composition;

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
 * Tests for Composition interface default methods and slot-based resolution.
 */
public class CompositionTests {

    @Nested
    class SlotBasedResolutionTests {

        @Test
        void placementsForSlot_returns_matching_placements() {
            final Composition composition = new CompositionWithMixedSlots();

            final List<ViewPlacement> primaryPlacements = composition.placementsForSlot(Slot.PRIMARY);
            final List<ViewPlacement> overlayPlacements = composition.placementsForSlot(Slot.OVERLAY);

            assertEquals(1, primaryPlacements.size());
            assertEquals(2, overlayPlacements.size());
        }

        @Test
        void placementsForSlot_returns_empty_when_no_matches() {
            final Composition composition = new CompositionWithPrimaryOnly();

            final List<ViewPlacement> overlayPlacements = composition.placementsForSlot(Slot.OVERLAY);

            assertTrue(overlayPlacements.isEmpty());
        }

        @Test
        void placementFor_finds_contract_class() {
            final Composition composition = new CompositionWithMixedSlots();

            final ViewPlacement placement = composition.placementFor(TestListContract.class);

            assertNotNull(placement);
            assertEquals(TestListContract.class, placement.contractClass());
            assertEquals(Slot.PRIMARY, placement.slot());
        }

        @Test
        void placementFor_returns_null_when_not_found() {
            final Composition composition = new CompositionWithPrimaryOnly();

            final ViewPlacement placement = composition.placementFor(TestEditContract.class);

            assertNull(placement);
        }
    }

    @Nested
    class CompositionInterfaceTests {

        @Test
        void views_returns_list() {
            final Composition composition = new TestComposition();

            final List<ViewPlacement> views = composition.views();

            assertNotNull(views);
        }

        @Test
        void empty_composition_has_no_views() {
            final Composition composition = new TestComposition();

            assertTrue(composition.views().isEmpty());
        }
    }

    // Test fixtures

    static class TestComposition implements Composition {
        @Override
        public List<ViewPlacement> views() {
            return List.of();
        }
    }

    static class CompositionWithPrimaryOnly extends TestComposition {
        @Override
        public List<ViewPlacement> views() {
            return List.of(
                    new ViewPlacement(Slot.PRIMARY, TestListContract.class, TestListContract::new)
            );
        }
    }

    static class CompositionWithMixedSlots extends TestComposition {
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
