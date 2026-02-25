package rsp.compositions.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentIntentTests {

    @Test
    void simple_intent_with_action_only() {
        AgentIntent intent = new AgentIntent("select_all");
        assertEquals("select_all", intent.action());
        assertTrue(intent.params().isEmpty());
        assertNull(intent.targetContract());
    }

    @Test
    void intent_with_params() {
        AgentIntent intent = new AgentIntent("page", Map.of("page", 3));
        assertEquals("page", intent.action());
        assertEquals(3, intent.params().get("page"));
    }

    @Test
    void params_are_immutable_copy() {
        var mutable = new java.util.HashMap<String, Object>();
        mutable.put("id", "5");
        AgentIntent intent = new AgentIntent("edit", mutable);
        mutable.put("extra", "bad");
        assertFalse(intent.params().containsKey("extra"));
    }

    @Test
    void null_action_throws() {
        assertThrows(IllegalArgumentException.class, () -> new AgentIntent(null));
    }

    @Test
    void blank_action_throws() {
        assertThrows(IllegalArgumentException.class, () -> new AgentIntent("  "));
    }

    @Test
    void null_params_defaults_to_empty_map() {
        AgentIntent intent = new AgentIntent("test", null, null);
        assertNotNull(intent.params());
        assertTrue(intent.params().isEmpty());
    }
}
