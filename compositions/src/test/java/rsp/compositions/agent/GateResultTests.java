package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class GateResultTests {

    private static final AgentAction TEST_ACTION =
        new AgentAction("delete", new EventKey.VoidKey("test.delete"), "Delete items", null);

    @Test
    void allow_carries_action_and_payload() {
        GateResult result = new GateResult.Allow(TEST_ACTION, "payload-value");
        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(TEST_ACTION, ((GateResult.Allow) result).action());
        assertEquals("payload-value", ((GateResult.Allow) result).rawPayload());
    }

    @Test
    void block_carries_reason() {
        GateResult result = new GateResult.Block("Not permitted");
        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Not permitted", ((GateResult.Block) result).reason());
    }

    @Test
    void confirm_carries_question_action_and_payload() {
        GateResult result = new GateResult.Confirm("Are you sure?", TEST_ACTION, null);
        assertInstanceOf(GateResult.Confirm.class, result);
        assertEquals("Are you sure?", ((GateResult.Confirm) result).question());
        assertEquals(TEST_ACTION, ((GateResult.Confirm) result).action());
        assertNull(((GateResult.Confirm) result).rawPayload());
    }

    @Test
    void exhaustive_switch() {
        GateResult result = new GateResult.Allow(TEST_ACTION, null);
        String outcome = switch (result) {
            case GateResult.Allow a -> "allow";
            case GateResult.Block b -> "block";
            case GateResult.Confirm c -> "confirm";
        };
        assertEquals("allow", outcome);
    }
}
