package rsp.dom;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class TreePositionPathTests {

    @Test
    public void produces_valid_empty_path() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertEquals(0, path.level());
        Assertions.assertEquals("", path.toString());
    }

    @Test
    public void produces_valid_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_1");
        Assertions.assertEquals(3, path.level());
        Assertions.assertEquals("1_2_1", path.toString());
    }

    @Test
    public void provides_correct_parent_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_1_3");
        Assertions.assertEquals(TreePositionPath.of("1_2_1"), path.parent());
    }

    @Test
    public void trows_exception_when_getting_root_path_parent() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertThrows(IllegalStateException.class, () -> path.parent());
    }

    @Test
    public void provides_first_path_of_next_level() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_1");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_1_1"), path.incLevel());
    }

    @Test
    public void provides_correct_sibling_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_9");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_10"), path.incSibling());
    }

    @Test
    public void trows_exception_when_getting_next_sibling_path_parent() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertThrows(IllegalStateException.class, () -> path.incSibling());
    }

    @Test
    public void provides_correct_specified_child_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_9");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_9_11"), path.addChild(11));
    }

    @Test
    public void should_comply_to_equals_hash_contract() {
        EqualsVerifier.forClass(TreePositionPath.class).verify();
    }
}
