package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class AgentActionTests {

    @Test
    void creates_action_with_all_fields() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.event");
        AgentAction action = new AgentAction("test", key, "A test action", null);

        assertEquals("test", action.action());
        assertSame(key, action.eventKey());
        assertEquals("A test action", action.description());
        assertNull(action.payloadDescription());
    }

    @Test
    void creates_action_with_payload_description() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("edit", String.class);
        AgentAction action = new AgentAction("edit", key, "Edit item", "String: row ID");

        assertEquals("String: row ID", action.payloadDescription());
    }

    @Test
    void null_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction(null, key, "desc", null));
    }

    @Test
    void blank_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("  ", key, "desc", null));
    }

    @Test
    void null_eventKey_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("test", null, "desc", null));
    }

    @Test
    void null_description_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("test", key, null, null));
    }
}
