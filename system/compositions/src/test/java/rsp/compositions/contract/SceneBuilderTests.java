package rsp.compositions.contract;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.Lookup;
import rsp.component.Subscriber;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.GroupPlacementPolicy;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.Router;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SceneBuilder} — particularly the parent-route handling
 * that branches on {@link rsp.compositions.layout.Layout#resolvePlacement}.
 */
class SceneBuilderTests {

    @Nested
    class ParentRouteWithModalPlacement {

        @Test
        void preserves_auto_open_for_overlay_contract() {
            // Default layout with no placement override → ALL_MODAL.
            final Composition composition = compositionWith(new DefaultLayout());
            final SceneBuilder builder = new SceneBuilder(
                    composition, EditContract.class, "/posts/:id", composition.layout());

            final Scene scene = builder.buildScene(testContext());

            assertEquals(ListContract.class, scene.routedRuntime().contractClass(),
                    "modal placement keeps the parent contract as the routed primary");
            assertTrue(scene.hasPreActivatedContracts(),
                    "modal placement pre-activates the overlay for LayerComponent auto-open");
            assertNotNull(scene.autoOpen());
            assertEquals(EditContract.class, scene.autoOpen().contractClass());
            assertEquals("/posts/:id", scene.autoOpen().routePattern());
        }

        @Test
        void explicit_modal_placement_keeps_auto_open() {
            final DefaultLayout layout = new DefaultLayout()
                    .placement(EditContract.class, Placement.MODAL);
            final Composition composition = compositionWith(layout);
            final SceneBuilder builder = new SceneBuilder(
                    composition, EditContract.class, "/posts/:id", layout);

            final Scene scene = builder.buildScene(testContext());

            assertEquals(ListContract.class, scene.routedRuntime().contractClass());
            assertTrue(scene.hasPreActivatedContracts());
        }
    }

    @Nested
    class ParentRouteWithInlinePlacement {

        @Test
        void routes_child_directly_as_primary() {
            final DefaultLayout layout = new DefaultLayout()
                    .placement(EditContract.class, Placement.INLINE.primary());
            final Composition composition = compositionWith(layout);
            final SceneBuilder builder = new SceneBuilder(
                    composition, EditContract.class, "/posts/:id", layout);

            final Scene scene = builder.buildScene(testContext());

            assertEquals(EditContract.class, scene.routedRuntime().contractClass(),
                    "inline placement routes the child contract directly as the primary");
            assertFalse(scene.hasPreActivatedContracts(),
                    "inline placement does NOT pre-activate any overlay");
            assertNull(scene.autoOpen(), "inline placement leaves autoOpen null");
        }

        @Test
        void inline_via_form_base_class_placement_routes_child_directly() {
            // Mirror the CrudApp pattern: blanket FormViewContract → inline.primary().
            // Here EditContract extends FormBaseContract; the rule applies via base class.
            final DefaultLayout layout = new DefaultLayout()
                    .placement(FormBaseContract.class, Placement.INLINE.primary());
            final Composition composition = compositionWithFormBase(layout);
            final SceneBuilder builder = new SceneBuilder(
                    composition, FormChildContract.class, "/posts/:id", layout);

            final Scene scene = builder.buildScene(testContext());

            assertEquals(FormChildContract.class, scene.routedRuntime().contractClass());
            assertFalse(scene.hasPreActivatedContracts());
        }

        @Test
        void all_inline_group_policy_routes_child_directly() {
            final DefaultLayout layout = new DefaultLayout()
                    .groupPlacementPolicy(GroupPlacementPolicy.ALL_INLINE);
            final Composition composition = compositionWith(layout);
            final SceneBuilder builder = new SceneBuilder(
                    composition, EditContract.class, "/posts/:id", layout);

            final Scene scene = builder.buildScene(testContext());

            assertEquals(EditContract.class, scene.routedRuntime().contractClass());
            assertFalse(scene.hasPreActivatedContracts());
        }
    }

    @Nested
    class NoParentRoute {

        @Test
        void routes_directly_regardless_of_placement() {
            // /posts has no parent route — placement decision is irrelevant.
            final DefaultLayout layout = new DefaultLayout()
                    .placement(ListContract.class, Placement.MODAL);
            final Composition composition = compositionWith(layout);
            final SceneBuilder builder = new SceneBuilder(
                    composition, ListContract.class, "/posts", layout);

            final Scene scene = builder.buildScene(testContext());

            assertEquals(ListContract.class, scene.routedRuntime().contractClass());
            assertFalse(scene.hasPreActivatedContracts());
            assertNull(scene.autoOpen());
        }
    }

    // --- Fixtures ----------------------------------------------------------

    private Composition compositionWith(DefaultLayout layout) {
        final Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new, () -> null)
                .bind(EditContract.class, EditContract::new, () -> null);
        final Router router = new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", EditContract.class);
        return new Composition(router, layout, group);
    }

    private Composition compositionWithFormBase(DefaultLayout layout) {
        final Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new, () -> null)
                .bind(FormChildContract.class, FormChildContract::new, () -> null);
        final Router router = new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", FormChildContract.class);
        return new Composition(router, layout, group);
    }

    private ComponentContext testContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, (CommandsEnqueue) _ -> {})
                .with(Subscriber.class, new NoOpSubscriber());
    }

    // --- Test contracts ----------------------------------------------------

    static class ListContract extends ViewContract {
        ListContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "List"; }
    }

    static class EditContract extends ViewContract {
        EditContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "Edit"; }
    }

    static abstract class FormBaseContract extends ViewContract {
        FormBaseContract(Lookup lookup) { super(lookup); }
        @Override public ComponentContext enrichContext(ComponentContext c) { return c; }
        @Override public String title() { return "Form"; }
    }

    static class FormChildContract extends FormBaseContract {
        FormChildContract(Lookup lookup) { super(lookup); }
    }

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {}

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
