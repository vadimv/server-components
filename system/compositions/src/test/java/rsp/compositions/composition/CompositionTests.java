package rsp.compositions.composition;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.block.Block;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the direct block-component bindings held by a composition. */
class CompositionTests {

    @Test
    void direct_bindings_are_resolved_as_fresh_block_components() {
        Group group = new Group().bind(ListBlock.class, ListBlock::new);

        assertTrue(group.hasBinding(ListBlock.class));
        assertTrue(group.resolveBlock(ListBlock.class) instanceof ListBlock);
        assertTrue(group.resolveBlock(ListBlock.class) instanceof ListBlock);
        assertTrue(group.resolveBlock(ListBlock.class) instanceof ListBlock);
    }

    @Test
    void composition_keeps_routes_and_all_bound_block_classes() {
        Router router = new Router()
                .route("/items", ListBlock.class)
                .route("/items/:id", EditBlock.class);
        Group group = new Group()
                .bind(ListBlock.class, ListBlock::new)
                .bind(CreateBlock.class, CreateBlock::new)
                .bind(EditBlock.class, EditBlock::new);

        Composition composition = new Composition(router, new DefaultLayout(), group);

        assertSame(router, composition.router());
        assertEquals(3, composition.blocks().blockClasses().size());
        assertTrue(composition.blocks().hasBinding(ListBlock.class));
        assertFalse(composition.blocks().hasBinding(UnknownBlock.class));
        assertThrows(UnsupportedOperationException.class,
                () -> composition.blocks().blockClasses().add(null));
    }

    @Test
    void nested_groups_expose_structure_paths_and_placement_ownership() {
        Group posts = new Group("Posts")
                .description("Blog posts")
                .bind(ListBlock.class, ListBlock::new);
        Group comments = new Group("Comments")
                .description("User comments")
                .bind(CreateBlock.class, CreateBlock::new);
        Group root = new Group("Admin").add(posts).add(comments);

        StructureNode tree = root.structureTree();

        assertEquals(List.of("Admin", "Posts"), root.groupPathFor(ListBlock.class).orElseThrow());
        assertEquals(List.of("Admin", "Comments"), root.groupPathFor(CreateBlock.class).orElseThrow());
        assertSame(posts, root.placementGroupFor(ListBlock.class).orElseThrow());
        assertEquals("Posts", tree.labelFor(ListBlock.class));
        assertTrue(tree.agentDescription().contains("Blog posts"));
        assertTrue(tree.agentDescription().contains("User comments"));
    }

    @Test
    void merged_groups_keep_bindings_from_each_group() {
        Group main = new Group("Main").bind(ListBlock.class, ListBlock::new);
        Group system = new Group().bind(CreateBlock.class, CreateBlock::new);

        Composition composition = new Composition(new Router(), new DefaultLayout(), main, system);

        assertEquals(2, composition.blocks().blockClasses().size());
        assertTrue(composition.blocks().hasBinding(ListBlock.class));
        assertTrue(composition.blocks().hasBinding(CreateBlock.class));
    }

    @Test
    void constructor_rejects_missing_required_composition_parts() {
        Group group = new Group();

        assertThrows(NullPointerException.class, () -> new Composition(null, new DefaultLayout(), group));
        assertThrows(IllegalArgumentException.class, () -> new Composition(new Router(), new DefaultLayout()));
    }

    static class TestBlock extends Block<String, Object> {
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

    static class ListBlock extends TestBlock {}
    static class CreateBlock extends TestBlock {}
    static class EditBlock extends TestBlock {}
    static class UnknownBlock extends TestBlock {}
}
