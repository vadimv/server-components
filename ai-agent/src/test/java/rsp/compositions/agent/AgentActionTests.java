package rsp.compositions.agent;

import rsp.compositions.contract.AgentAction;
import rsp.compositions.contract.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class AgentActionTests {

    @Test
    void creates_action_with_all_fields() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.event");
        AgentAction action = new AgentAction("test", key, "A test action");

        assertEquals("test", action.action());
        assertSame(key, action.eventKey());
        assertEquals("A test action", action.description());
        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void creates_action_with_schema() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("edit", String.class);
        AgentAction action = new AgentAction("edit", key, "Edit item",
            new PayloadSchema.StringValue("row ID"));

        assertInstanceOf(PayloadSchema.StringValue.class, action.schema());
        assertEquals("row ID", ((PayloadSchema.StringValue) action.schema()).description());
    }

    @Test
    void null_schema_defaults_to_none() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        AgentAction action = new AgentAction("test", key, "desc", null);

        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void parser_null_when_not_provided() {
        EventKey.SimpleKey<Integer> key = new EventKey.SimpleKey<>("page", Integer.class);
        AgentAction action = new AgentAction("page", key, "Go to page",
            new PayloadSchema.IntegerValue("page number"));

        // parsePayload is null — agent framework resolves via PayloadSchemas.toParser() at dispatch time
        assertNull(action.parsePayload());
    }

    @Test
    void parser_resolved_via_payload_schemas() {
        EventKey.SimpleKey<Integer> key = new EventKey.SimpleKey<>("page", Integer.class);
        AgentAction action = new AgentAction("page", key, "Go to page",
            new PayloadSchema.IntegerValue("page number"));

        var parser = PayloadSchemas.toParser(action.schema());
        assertEquals(3, parser.apply(AgentPayload.of(3)));
    }

    @Test
    void null_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction(null, key, "desc"));
    }

    @Test
    void blank_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("  ", key, "desc"));
    }

    @Test
    void null_eventKey_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("test", null, "desc"));
    }

    @Test
    void null_description_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new AgentAction("test", key, null));
    }
}
