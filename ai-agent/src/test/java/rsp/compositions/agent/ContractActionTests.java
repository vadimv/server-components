package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;


import rsp.compositions.contract.ContractAction;
import rsp.compositions.contract.PayloadSchema;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class ContractActionTests {

    @Test
    void creates_action_with_all_fields() {
        EventKey.VoidKey key = new EventKey.VoidKey("test.event");
        ContractAction action = new ContractAction("test", key, "A test action");

        assertEquals("test", action.action());
        assertSame(key, action.eventKey());
        assertEquals("A test action", action.description());
        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void creates_action_with_schema() {
        EventKey.SimpleKey<String> key = new EventKey.SimpleKey<>("edit", String.class);
        ContractAction action = new ContractAction("edit", key, "Edit item",
            new PayloadSchema.StringValue("row ID"));

        assertInstanceOf(PayloadSchema.StringValue.class, action.schema());
        assertEquals("row ID", ((PayloadSchema.StringValue) action.schema()).description());
    }

    @Test
    void null_schema_defaults_to_none() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        ContractAction action = new ContractAction("test", key, "desc", null);

        assertInstanceOf(PayloadSchema.None.class, action.schema());
    }

    @Test
    void parser_derived_from_schema() {
        EventKey.SimpleKey<Integer> key = new EventKey.SimpleKey<>("page", Integer.class);
        ContractAction action = new ContractAction("page", key, "Go to page",
            new PayloadSchema.IntegerValue("page number"));

        assertNotNull(action.parsePayload());
        assertEquals(3, action.parsePayload().apply(ContractActionPayload.of(3)));
    }

    @Test
    void null_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new ContractAction(null, key, "desc"));
    }

    @Test
    void blank_action_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new ContractAction("  ", key, "desc"));
    }

    @Test
    void null_eventKey_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new ContractAction("test", null, "desc"));
    }

    @Test
    void null_description_throws() {
        EventKey.VoidKey key = new EventKey.VoidKey("test");
        assertThrows(IllegalArgumentException.class,
            () -> new ContractAction("test", key, null));
    }
}
