package rsp.compositions.composition;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.compositions.block.Block;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.BlockRoutes;

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
        assertSame(ListBlock.class, group.target(ListBlock.class).key());
    }

    @Test
    void object_keys_can_bind_the_same_block_class_with_distinct_factories() {
        Object postsKey = new Object();
        Object archivedPostsKey = new Object();
        Group group = new Group("Posts")
                .bind(postsKey, KeyedBlock.class, () -> new KeyedBlock("Current"))
                .bind(archivedPostsKey, KeyedBlock.class, () -> new KeyedBlock("Archived"));

        assertSame(postsKey, group.target(postsKey).key());
        assertEquals(KeyedBlock.class, group.target(postsKey).blockClass());
        assertEquals("Current", group.resolveBlock(postsKey).title());
        assertEquals("Archived", group.resolveBlock(archivedPostsKey).title());
        assertEquals(2, group.blockTargets().size());
        assertEquals(1, group.blockClasses().size());
    }

    @Test
    void routed_bindings_support_explicit_keys_for_repeated_block_classes() {
        Object current = new Object();
        Object archived = new Object();
        Group group = new Group("Posts")
                .add("/posts", current, KeyedBlock.class, () -> new KeyedBlock("Current"))
                .add("/posts/archived", archived, KeyedBlock.class, () -> new KeyedBlock("Archived"));

        Composition composition = new Composition(new DefaultLayout(), group);

        assertSame(current, composition.routes().match(rsp.url.Path.of("/posts"))
                .orElseThrow().target().key());
        assertSame(archived, composition.routes().match(rsp.url.Path.of("/posts/archived"))
                .orElseThrow().target().key());
    }

    @Test
    void composition_derives_routes_from_groups_and_keeps_non_routed_blocks() {
        Group group = new Group()
                .add("/items", ListBlock.class, ListBlock::new)
                .route("/", ListBlock.class)
                .bind(CreateBlock.class, CreateBlock::new)
                .add("/items/{id}", EditBlock.class, EditBlock::new);

        Composition composition = new Composition(new DefaultLayout(), group);

        assertEquals(3, composition.routes().routes().size());
        assertEquals(3, composition.blocks().blockClasses().size());
        assertTrue(composition.blocks().hasBinding(ListBlock.class));
        assertFalse(composition.blocks().hasBinding(UnknownBlock.class));
        assertSame(ListBlock.class, composition.routes().match(rsp.url.Path.of("/"))
                .orElseThrow().target().key());
        assertSame(EditBlock.class, composition.routes().match(rsp.url.Path.of("/items/42"))
                .orElseThrow().target().key());
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

        Composition composition = new Composition(BlockRoutes.builder(), new DefaultLayout(), main, system);

        assertEquals(2, composition.blocks().blockClasses().size());
        assertTrue(composition.blocks().hasBinding(ListBlock.class));
        assertTrue(composition.blocks().hasBinding(CreateBlock.class));
    }

    @Test
    void constructor_rejects_missing_required_composition_parts() {
        Group group = new Group();

        assertThrows(NullPointerException.class, () -> new Composition(
                (rsp.url.routing.RouteTable<rsp.compositions.block.BlockTarget>) null,
                new DefaultLayout(), group));
        assertThrows(IllegalArgumentException.class, () -> new Composition(BlockRoutes.builder(), new DefaultLayout()));
    }

    @Test
    void composition_rejects_an_unbound_route_without_sealing_inputs() {
        Object postsKey = new Object();
        BlockRoutes.Builder routes = BlockRoutes.builder().route("/posts", postsKey, ListBlock.class);
        Group group = new Group("Posts");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new Composition(routes, new DefaultLayout(), group));

        assertTrue(error.getMessage().contains("/posts"));
        group.bind(postsKey, ListBlock.class, ListBlock::new);
        Composition composition = new Composition(routes, new DefaultLayout(), group);
        assertSame(postsKey, composition.routes().match(rsp.url.Path.of("/posts"))
                .orElseThrow().target().key());
    }

    @Test
    void composition_rejects_an_unbound_layout_key_without_sealing_the_group() {
        Object explorerKey = new Object();
        Group group = new Group();
        DefaultLayout layout = new DefaultLayout().leftSidebar(explorerKey);

        assertThrows(IllegalArgumentException.class,
                () -> new Composition(BlockRoutes.builder(), layout, group));

        group.bind(explorerKey, ListBlock.class, ListBlock::new);
        assertEquals(1, new Composition(BlockRoutes.builder(), layout, group).blocks().blockTargets().size());
    }

    @Test
    void duplicate_equal_keys_are_rejected_locally_and_across_groups() {
        Object first = new String("posts");
        Object equal = new String("posts");
        Group local = new Group().bind(first, ListBlock.class, ListBlock::new);

        assertThrows(IllegalArgumentException.class,
                () -> local.bind(equal, EditBlock.class, EditBlock::new));

        Group root = new Group("Root")
                .add(new Group("One").bind(first, ListBlock.class, ListBlock::new))
                .add(new Group("Two").bind(equal, EditBlock.class, EditBlock::new));
        assertThrows(IllegalArgumentException.class,
                () -> new Composition(BlockRoutes.builder(), new DefaultLayout(), root));
    }

    @Test
    void derived_routes_are_validated_across_the_complete_group_tree() {
        Group first = new Group("First")
                .add("/items/{id}", ListBlock.class, ListBlock::new);
        Group second = new Group("Second")
                .add("/items/{name}", EditBlock.class, EditBlock::new);
        Group root = new Group("Root").add(first).add(second);

        assertThrows(IllegalArgumentException.class,
                () -> new Composition(new DefaultLayout(), root));

        first.description("Still mutable after failed composition construction");
    }

    @Test
    void successful_composition_snapshots_routes_and_seals_all_groups() {
        Object postsKey = new Object();
        BlockRoutes.Builder routes = BlockRoutes.builder().route("/posts", postsKey, ListBlock.class);
        Group child = new Group("Posts").bind(postsKey, ListBlock.class, ListBlock::new);
        Group root = new Group("Root").add(child);

        Composition composition = new Composition(routes, new DefaultLayout(), root);

        routes.route("/other", postsKey, ListBlock.class);
        assertEquals(1, composition.routes().routes().size());
        assertThrows(IllegalStateException.class,
                () -> root.add(new Group("Other")));
        assertThrows(IllegalStateException.class,
                () -> child.description("Changed"));
        assertThrows(IllegalStateException.class,
                () -> child.bind(new Object(), EditBlock.class, EditBlock::new));
        assertThrows(IllegalStateException.class,
                () -> child.route("/alias", ListBlock.class));
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

    static class KeyedBlock extends TestBlock {
        private final String title;

        KeyedBlock(String title) {
            this.title = title;
        }

        @Override
        public String title() {
            return title;
        }
    }
}
