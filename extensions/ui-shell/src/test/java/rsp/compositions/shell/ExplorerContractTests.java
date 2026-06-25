package rsp.compositions.shell;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.ContextKeys;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.NavigationNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static rsp.compositions.contract.EventKeys.SET_PRIMARY;

class ExplorerContractTests {

    @Test
    void enriches_context_with_navigation_tree_from_structure() {
        StructureNode structure = new StructureNode("Admin", null,
                List.of(new StructureNode("Posts", null, List.of(), List.of()),
                        new StructureNode("Comments", null, List.of(), List.of())),
                List.of());
        ExplorerContract contract = new ExplorerContract(new TestLookup(), structure);

        assertEquals("Explorer", contract.title());

        ComponentContext context = contract.enrichContext(new ComponentContext());
        NavigationNode tree = context.get(ContextKeys.NAVIGATION_TREE);

        assertNotNull(tree);
        assertEquals("Admin", tree.label());
        assertEquals(2, tree.children().size());
        assertEquals(List.of("Posts", "Comments"),
                tree.children().stream().map(NavigationNode::label).toList());
    }

    @Test
    void relays_open_request_as_set_primary_command() {
        TestLookup lookup = new TestLookup();
        ExplorerContract contract = new ExplorerContract(lookup,
                new StructureNode("Admin", null, List.of(), List.of()));
        assertNotNull(contract);

        NavigationEntry entry = new NavigationEntry("Posts", "Posts", HeaderContract.class, "/posts");
        lookup.publish(ExplorerContract.REQUEST_OPEN_CONTRACT, entry);

        assertTrue(lookup.wasPublished(SET_PRIMARY));
        assertEquals(HeaderContract.class, lookup.getLastPublishedPayload(SET_PRIMARY));
    }
}
