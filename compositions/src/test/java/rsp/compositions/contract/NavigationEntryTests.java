package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.routing.Router;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NavigationEntryTests {

    @Nested
    class FromCompositionsTests {

        @Test
        void extracts_routable_contracts() {
            final Composition comp = new Composition(
                    new Router().route("/items", TestListContract.class),
                    new UiRegistry()
                            .register(TestListContract.class, TestListContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertEquals(1, entries.size());
            assertEquals("Test", entries.get(0).categoryKey());
            assertEquals("Test", entries.get(0).label());
            assertEquals(TestListContract.class, entries.get(0).contractClass());
            assertEquals("/items", entries.get(0).route());
        }

        @Test
        void label_comes_from_metadata_title() {
            final Composition comp = new Composition(
                    new Router().route("/entities", TestEntityContract.class),
                    new UiRegistry()
                            .register(TestEntityContract.class, TestEntityContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertEquals("Test Entity", entries.get(0).label());
        }

        @Test
        void deduplicates_by_category_key() {
            // Two contracts with same derived category key — only first should appear
            final Composition comp = new Composition(
                    new Router()
                            .route("/items", TestListContract.class)
                            .route("/items2", TestListContract2.class),
                    new UiRegistry()
                            .register(TestListContract.class, TestListContract::new, () -> null)
                            .register(TestListContract2.class, TestListContract2::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertEquals(1, entries.size());
            assertEquals(TestListContract.class, entries.get(0).contractClass());
        }

        @Test
        void ignores_contracts_without_routes() {
            final Composition comp = new Composition(
                    new Router(),
                    new UiRegistry()
                            .register(TestOverlayContract.class, TestOverlayContract::new, () -> null)
                            .register(TestSidebarContract.class, TestSidebarContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertTrue(entries.isEmpty());
        }

        @Test
        void ignores_parameterized_routes() {
            final Composition comp = new Composition(
                    new Router().route("/items/:id", TestEntityContract.class),
                    new UiRegistry()
                            .register(TestEntityContract.class, TestEntityContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertTrue(entries.isEmpty());
        }

        @Test
        void handles_null_input() {
            List<NavigationEntry> entries = NavigationEntry.fromCompositions(null);
            assertTrue(entries.isEmpty());
        }

        @Test
        void handles_empty_list() {
            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of());
            assertTrue(entries.isEmpty());
        }

        @Test
        void does_not_require_contract_instantiation() {
            final Composition comp = new Composition(
                    new Router()
                            .route("/failing", FailingContract.class)
                            .route("/items", TestListContract.class),
                    new UiRegistry()
                            .register(FailingContract.class, FailingContract::new, () -> null)
                            .register(TestListContract.class, TestListContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp));

            assertEquals(2, entries.size());
        }

        @Test
        void collects_from_multiple_compositions() {
            final Composition comp1 = new Composition(
                    new Router().route("/items", TestListContract.class),
                    new UiRegistry()
                            .register(TestListContract.class, TestListContract::new, () -> null)
            );
            final Composition comp2 = new Composition(
                    new Router().route("/entities", TestEntityContract.class),
                    new UiRegistry()
                            .register(TestEntityContract.class, TestEntityContract::new, () -> null)
            );

            List<NavigationEntry> entries = NavigationEntry.fromCompositions(List.of(comp1, comp2));

            assertEquals(2, entries.size());
            assertEquals("Test", entries.get(0).categoryKey());
            assertEquals("Test Entity", entries.get(1).categoryKey());
        }
    }

    // === Test contract stubs ===

    record TestEntity(String id, String name) {}

    static class TestListContract extends ViewContract {
        TestListContract(Lookup lookup) { super(lookup); }
        @Override public String title() { return "Test"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }

    /** Same category key as TestListContract — used for deduplication test */
    static class TestListContract2 extends ViewContract {
        TestListContract2(Lookup lookup) { super(lookup); }
        @Override public String title() { return "Test2"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }

    static class TestOverlayContract extends ViewContract {
        TestOverlayContract(Lookup lookup) { super(lookup); }
        @Override public String title() { return "Overlay"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }

    static class TestSidebarContract extends ViewContract {
        TestSidebarContract(Lookup lookup) { super(lookup); }
        @Override public String title() { return "Sidebar"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }

    static class TestEntityContract extends ViewContract {
        TestEntityContract(Lookup lookup) { super(lookup); }
        @Override public String title() { return "TestEntity"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }

    static class FailingContract extends ViewContract {
        FailingContract(Lookup lookup) {
            super(lookup);
            throw new RuntimeException("Intentional failure for test");
        }
        @Override public String title() { return "Fail"; }
        @Override public ComponentContext enrichContext(ComponentContext context) { return context; }
    }
}
