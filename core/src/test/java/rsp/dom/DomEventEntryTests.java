package rsp.dom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class DomEventEntryTests {

    @Test
    void should_comply_to_equals_hash_contract() {
        final DomEventEntry e1 = new DomEventEntry("click", new DomEventEntry.Target(TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   DomEventEntry.NO_MODIFIER);
        final DomEventEntry e2 = new DomEventEntry("click", new DomEventEntry.Target(  TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   DomEventEntry.NO_MODIFIER);
        Assertions.assertEquals(e1, e2);
        Assertions.assertEquals(e1.hashCode(), e2.hashCode());

        final DomEventEntry e3 = new DomEventEntry("click", new DomEventEntry.Target(  TreePositionPath.of("1_1")),
                                  ctx -> {},
                                  false,
                                   DomEventEntry.NO_MODIFIER);
        Assertions.assertNotEquals(e1, e3);

    }
}
