package rsp.compositions.agent;

import rsp.compositions.block.BlockActionPayload;


import rsp.compositions.block.BlockAction;
import rsp.compositions.block.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class BlockActionTests {

    @Test
    void creates_action_with_all_fields() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.event");
        BlockAction action = new BlockAction("test", key, "A test action");

        assertEquals("test", action.action());
        assertSame(key, action.eventKey());
        assertEquals("A test action", action.description());
        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void creates_action_with_schema() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("edit", String.class);
        BlockAction action = new BlockAction("edit", key, "Edit item",
            new PayloadSchema.StringValue("row ID"));

        assertInstanceOf(PayloadSchema.StringValue.class, action.schema());
        assertEquals("row ID", ((PayloadSchema.StringValue) action.schema()).description());
    }

    @Test
    void null_schema_defaults_to_none() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        // Cast disambiguates against the (String, EventKey, String, DispatchEffect) overload.
        BlockAction action = new BlockAction("test", key, "desc", (PayloadSchema) null);

        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void parser_derived_from_schema() {
        EventKey.SimpleKey<Integer> key = new EventKey.SimpleKey<>("page", Integer.class);
        BlockAction action = new BlockAction("page", key, "Go to page",
            new PayloadSchema.IntegerValue("page number"));

        assertNotNull(action.parsePayload());
        assertEquals(3, action.parsePayload().apply(BlockActionPayload.of(3)));
    }

    @Test
    void null_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new BlockAction(null, key, "desc"));
    }

    @Test
    void blank_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new BlockAction("  ", key, "desc"));
    }

    @Test
    void null_eventKey_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlockAction("test", null, "desc"));
    }

    @Test
    void null_description_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new BlockAction("test", key, null));
    }
}
