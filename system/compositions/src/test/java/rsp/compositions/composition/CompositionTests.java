package rsp.compositions.composition;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.contract.ContractNodeComponent;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the direct contract-component bindings held by a composition. */
class CompositionTests {

    @Test
    void direct_bindings_are_resolved_as_fresh_contract_components() {
        Group group = new Group().bind(ListContract.class, ListContract::new);

        assertTrue(group.hasBinding(ListContract.class));
        assertTrue(group.resolveComponent(ListContract.class) instanceof ListContract);
        assertTrue(group.resolveComponent(ListContract.class) instanceof ListContract);
    }

    @Test
    void composition_keeps_routes_and_all_bound_contract_classes() {
        Router router = new Router()
                .route("/items", ListContract.class)
                .route("/items/:id", EditContract.class);
        Group group = new Group()
                .bind(ListContract.class, ListContract::new)
                .bind(CreateContract.class, CreateContract::new)
                .bind(EditContract.class, EditContract::new);

        Composition composition = new Composition(router, new DefaultLayout(), group);

        assertSame(router, composition.router());
        assertEquals(3, composition.contracts().contractClasses().size());
        assertTrue(composition.contracts().hasBinding(ListContract.class));
        assertFalse(composition.contracts().hasBinding(UnknownContract.class));
        assertThrows(UnsupportedOperationException.class,
                () -> composition.contracts().contractClasses().add(null));
    }

    @Test
    void nested_groups_expose_structure_paths_and_placement_ownership() {
        Group posts = new Group("Posts")
                .description("Blog posts")
                .bind(ListContract.class, ListContract::new);
        Group comments = new Group("Comments")
                .description("User comments")
                .bind(CreateContract.class, CreateContract::new);
        Group root = new Group("Admin").add(posts).add(comments);

        StructureNode tree = root.structureTree();

        assertEquals(List.of("Admin", "Posts"), root.groupPathFor(ListContract.class).orElseThrow());
        assertEquals(List.of("Admin", "Comments"), root.groupPathFor(CreateContract.class).orElseThrow());
        assertSame(posts, root.placementGroupFor(ListContract.class).orElseThrow());
        assertEquals("Posts", tree.labelFor(ListContract.class));
        assertTrue(tree.agentDescription().contains("Blog posts"));
        assertTrue(tree.agentDescription().contains("User comments"));
    }

    @Test
    void merged_groups_keep_bindings_from_each_group() {
        Group main = new Group("Main").bind(ListContract.class, ListContract::new);
        Group system = new Group().bind(CreateContract.class, CreateContract::new);

        Composition composition = new Composition(new Router(), new DefaultLayout(), main, system);

        assertEquals(2, composition.contracts().contractClasses().size());
        assertTrue(composition.contracts().hasBinding(ListContract.class));
        assertTrue(composition.contracts().hasBinding(CreateContract.class));
    }

    @Test
    void constructor_rejects_missing_required_composition_parts() {
        Group group = new Group();

        assertThrows(NullPointerException.class, () -> new Composition(null, new DefaultLayout(), group));
        assertThrows(IllegalArgumentException.class, () -> new Composition(new Router(), new DefaultLayout()));
    }

    static class TestContract extends ContractNodeComponent<String, Object> {
        @Override
        public ComponentStateSupplier<String> initStateSupplier() {
            return (_, _) -> "ready";
        }

        @Override
        public ComponentView<String, Object> componentView() {
            return _ -> _ -> null;
        }

        @Override
        public String title() {
            return "Test";
        }
    }

    static class ListContract extends TestContract {}
    static class CreateContract extends TestContract {}
    static class EditContract extends TestContract {}
    static class UnknownContract extends TestContract {}
}
