package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.NavigationNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExplorerContractTests {

    @Test
    void initial_state_builds_navigation_tree_from_structure() {
        StructureNode structure = new StructureNode("Admin", null,
                List.of(new StructureNode("Posts", null, List.of(), List.of()),
                        new StructureNode("Comments", null, List.of(), List.of())),
                List.of());
        ExplorerContract contract = new ExplorerContract(structure);

        NavigationNode tree = contract.initStateSupplier().getState(null, new ComponentContext()).tree();

        assertEquals("Explorer", contract.title());
        assertNotNull(tree);
        assertEquals("Admin", tree.label());
        assertEquals(List.of("Posts", "Comments"),
                tree.children().stream().map(NavigationNode::label).toList());
    }

    @Test
    void open_intent_carries_the_selected_navigation_entry() {
        NavigationEntry entry = new NavigationEntry("Posts", "Posts", HeaderContract.class, "/posts");

        ExplorerView.OpenContract intent = new ExplorerView.OpenContract(entry);

        assertEquals(entry, intent.entry());
    }
}
