package rsp.dom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class EventEntryTests {

    @Test
    void should_comply_to_equals_hash_contract() {
        final EventEntry e1 = new EventEntry("click", new EventEntry.Target(TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   EventEntry.NO_MODIFIER);
        final EventEntry e2 = new EventEntry("click", new EventEntry.Target(  TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   EventEntry.NO_MODIFIER);
        Assertions.assertEquals(e1, e2);
        Assertions.assertEquals(e1.hashCode(), e2.hashCode());

        final EventEntry e3 = new EventEntry("click", new EventEntry.Target(  TreePositionPath.of("1_1")),
                                  ctx -> {},
                                  false,
                                   EventEntry.NO_MODIFIER);
        Assertions.assertNotEquals(e1, e3);

    }
}
