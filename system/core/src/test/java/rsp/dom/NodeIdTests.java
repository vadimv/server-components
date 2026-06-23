package rsp.dom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeIdTests {

    @Test
    void of_tree_position_path_produces_positional_segments() {
        assertEquals("1_2_3", NodeId.of(new TreePositionPath(1, 2, 3)).toString());
        assertEquals("1", NodeId.of(new TreePositionPath(1)).toString());
        assertEquals("", NodeId.of(new TreePositionPath()).toString());
    }

    @Test
    void inc_level_and_sibling_walk_positionally() {
        final NodeId root = NodeId.of(new TreePositionPath(1));
        final NodeId firstChild = root.incLevel();
        assertEquals("1_1", firstChild.toString());
        assertEquals("1_2", firstChild.incSibling().toString());
        assertEquals("1", firstChild.parent().toString());
    }

    @Test
    void child_appends_key_segment() {
        final NodeId parent = NodeId.of(new TreePositionPath(1, 2));
        assertEquals("1_2_kn42", parent.child("kn42").toString());
        assertEquals("1_2_kspost-7", parent.child("kspost-7").toString());
    }

    @Test
    void inc_sibling_on_keyed_segment_fails_loudly() {
        final NodeId keyed = NodeId.of(new TreePositionPath(1)).child("kn42");
        assertThrows(IllegalStateException.class, keyed::incSibling);
    }

    @Test
    void round_trips_through_string_form() {
        final NodeId id = NodeId.of(new TreePositionPath(1, 2)).child("kn5").incLevel();
        assertEquals(id, NodeId.of(id.toString()));
        assertEquals("1_2_kn5_1", id.toString());
    }

    @Test
    void equality_is_by_segments() {
        assertEquals(NodeId.of(new TreePositionPath(1, 2)), NodeId.of("1_2"));
        assertNotEquals(NodeId.of("1_2"), NodeId.of("1_2_kn2"));
    }
}
