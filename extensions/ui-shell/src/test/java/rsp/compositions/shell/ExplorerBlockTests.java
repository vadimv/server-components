package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.block.ContextKeys;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.NavigationEntry;
import rsp.compositions.block.NavigationNode;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.BlockRoutes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExplorerBlockTests {

    @Test
    void initial_state_builds_navigation_tree_from_structure() {
        StructureNode structure = new StructureNode("Admin", null,
                List.of(new StructureNode("Posts", null, List.of(), List.of()),
                        new StructureNode("Comments", null, List.of(), List.of())),
                List.of());
        ExplorerBlock block = new ExplorerBlock(structure);

        NavigationNode tree = block.initStateSupplier().getState(null, new ComponentContext()).tree();

        assertEquals("Explorer", block.title());
        assertNotNull(tree);
        assertEquals("Admin", tree.label());
        assertEquals(List.of("Posts", "Comments"),
                tree.children().stream().map(NavigationNode::label).toList());
    }

    @Test
    void open_intent_carries_the_selected_navigation_entry() {
        NavigationEntry entry = new NavigationEntry("Posts", "Posts", HeaderBlock.class, "/posts");

        ExplorerView.OpenBlock intent = new ExplorerView.OpenBlock(entry);

        assertEquals(entry, intent.entry());
    }

    @Test
    void route_lookup_skips_compositions_that_do_not_bind_the_navigation_key() {
        Group auth = new Group().bind(HeaderBlock.class, HeaderBlock::new);
        Composition authComposition = new Composition(
                BlockRoutes.builder().route("/auth", HeaderBlock.class), new DefaultLayout(), auth);

        Group application = new Group("Application")
                .bind(ExplorerBlock.class, () -> new ExplorerBlock(new StructureNode(null, null,
                        List.of(), List.of())));
        Composition applicationComposition = new Composition(
                BlockRoutes.builder().route("/application", ExplorerBlock.class),
                new DefaultLayout(), application);
        ExplorerBlock explorer = new ExplorerBlock(application.structureTree());
        ComponentContext context = new ComponentContext().with(
                ContextKeys.APP_COMPOSITIONS, List.of(authComposition, applicationComposition));

        NavigationNode tree = explorer.initStateSupplier().getState(null, context).tree();

        assertEquals("/application", tree.entry().route());
    }
}
