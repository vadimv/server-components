package rsp.compositions.contract;

import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies parent-route placement with direct contract components. */
class SceneBuilderTests {

    @Test
    void modal_child_keeps_parent_as_primary_and_auto_opens_child() {
        Composition composition = compositionWith(new DefaultLayout());

        Scene scene = new SceneBuilder(composition, EditContract.class, "/posts/:id", composition.layout())
                .buildScene(testContext());

        assertEquals(ListContract.class, scene.routedDescriptor().contractClass());
        assertTrue(scene.hasPreActivatedContracts());
        assertNotNull(scene.autoOpen());
        assertEquals(EditContract.class, scene.autoOpen().contractClass());
    }

    @Test
    void inline_child_becomes_primary_without_an_overlay() {
        DefaultLayout layout = new DefaultLayout().placement(EditContract.class, Placement.INLINE.primary());
        Composition composition = compositionWith(layout);

        Scene scene = new SceneBuilder(composition, EditContract.class, "/posts/:id", layout)
                .buildScene(testContext());

        assertEquals(EditContract.class, scene.routedDescriptor().contractClass());
        assertFalse(scene.hasPreActivatedContracts());
        assertNull(scene.autoOpen());
    }

    @Test
    void base_component_placement_applies_to_a_child_contract() {
        DefaultLayout layout = new DefaultLayout().placement(FormBaseContract.class, Placement.INLINE.primary());
        Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new)
                .bind(FormChildContract.class, FormChildContract::new);
        Composition composition = new Composition(new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", FormChildContract.class), layout, group);

        Scene scene = new SceneBuilder(composition, FormChildContract.class, "/posts/:id", layout)
                .buildScene(testContext());

        assertEquals(FormChildContract.class, scene.routedDescriptor().contractClass());
        assertFalse(scene.hasPreActivatedContracts());
    }

    @Test
    void group_inline_policy_routes_child_directly() {
        DefaultLayout layout = new DefaultLayout().groupPlacementPolicy(GroupPlacementPolicy.ALL_INLINE);
        Composition composition = compositionWith(layout);

        Scene scene = new SceneBuilder(composition, EditContract.class, "/posts/:id", layout)
                .buildScene(testContext());

        assertEquals(EditContract.class, scene.routedDescriptor().contractClass());
        assertFalse(scene.hasPreActivatedContracts());
    }

    private Composition compositionWith(DefaultLayout layout) {
        Group group = new Group("Posts")
                .bind(ListContract.class, ListContract::new)
                .bind(EditContract.class, EditContract::new);
        return new Composition(new Router()
                .route("/posts", ListContract.class)
                .route("/posts/:id", EditContract.class), layout, group);
    }

    private ComponentContext testContext() {
        return new ComponentContext()
                .with(CommandsEnqueue.class, (CommandsEnqueue) _ -> {})
                .with(Subscriber.class, new NoOpSubscriber());
    }

    static class ListContract extends ContractNodeComponent<String, Object> {
        @Override public ComponentStateSupplier<String> initStateSupplier() { return (_, _) -> "ready"; }
        @Override public ComponentView<String, Object> componentView() { return _ -> _ -> null; }
        @Override public String title() { return "List"; }
    }

    static class EditContract extends ListContract {
        @Override public String title() { return "Edit"; }
    }

    static abstract class FormBaseContract extends ListContract {}
    static class FormChildContract extends FormBaseContract {}

    private static final class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType,
                                          java.util.function.Consumer<EventContext> eventHandler,
                                          boolean preventDefault,
                                          DomEventEntry.Modifier modifier) {
        }

        @Override
        public Lookup.Registration addComponentEventHandler(String eventType,
                                                            java.util.function.Consumer<ComponentEventEntry.EventContext> eventHandler,
                                                            boolean preventDefault) {
            return () -> {};
        }
    }
}
