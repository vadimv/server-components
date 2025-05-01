package rsp.dom;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


class EventTests {

    @Test
    void should_comply_to_equals_hash_contract() {
        final Event e1 = new Event(new Event.Target("click",  TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   Event.NO_MODIFIER);
        final Event e2 = new Event(new Event.Target("click",  TreePositionPath.of("1")),
                                   ctx -> {},
                                   false,
                                   Event.NO_MODIFIER);
        Assertions.assertEquals(e1, e2);
        Assertions.assertEquals(e1.hashCode(), e2.hashCode());

        final Event e3 = new Event(new Event.Target("click",  TreePositionPath.of("1_1")),
                                  ctx -> {},
                                  false,
                                   Event.NO_MODIFIER);
        Assertions.assertNotEquals(e1, e3);

    }
}
