package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.block.NavigationEntry;
import rsp.compositions.block.NavigationNode;

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
}
