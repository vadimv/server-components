package rsp.dom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class TreePositionPathTests {

    @Test
    void produces_valid_empty_path() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertEquals(0, path.level());
        Assertions.assertEquals("", path.toString());
    }

    @Test
    void produces_valid_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_1");
        Assertions.assertEquals(3, path.level());
        Assertions.assertEquals("1_2_1", path.toString());
    }

    @Test
    void provides_correct_parent_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_1_3");
        Assertions.assertEquals(TreePositionPath.of("1_2_1"), path.parent());
    }

    @Test
    void trows_exception_when_getting_root_path_parent() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertThrows(IllegalStateException.class, () -> path.parent());
    }

    @Test
    void provides_first_path_of_next_level() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_1");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_1_1"), path.incLevel());
    }

    @Test
    void provides_correct_sibling_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_9");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_10"), path.incSibling());
    }

    @Test
    void trows_exception_when_getting_next_sibling_path_parent() {
        final TreePositionPath path = TreePositionPath.of("");
        Assertions.assertThrows(IllegalStateException.class, () -> path.incSibling());
    }

    @Test
    void provides_correct_specified_child_path() {
        final TreePositionPath path = TreePositionPath.of("1_2_2_9");
        Assertions.assertEquals(TreePositionPath.of("1_2_2_9_11"), path.addChild(11));
    }

    @Test
    void should_comply_to_equals_hash_contract() {
        final TreePositionPath path1 = TreePositionPath.of("1_2_2_9");
        final TreePositionPath path2 = TreePositionPath.of("1_2_2_9");
        Assertions.assertEquals(path1, path2);
        Assertions.assertEquals(path1.hashCode(), path2.hashCode());
        final TreePositionPath path3 = TreePositionPath.of("1_2_2_9_1");
        Assertions.assertNotEquals(path1, path3);
    }
}
