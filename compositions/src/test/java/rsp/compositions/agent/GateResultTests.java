package rsp.compositions.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GateResultTests {

    @Test
    void allow_carries_intent() {
        AgentIntent intent = new AgentIntent("navigate");
        GateResult result = new GateResult.Allow(intent);
        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(intent, ((GateResult.Allow) result).intent());
    }

    @Test
    void block_carries_reason() {
        GateResult result = new GateResult.Block("Not permitted");
        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Not permitted", ((GateResult.Block) result).reason());
    }

    @Test
    void confirm_carries_question_and_intent() {
        AgentIntent intent = new AgentIntent("delete");
        GateResult result = new GateResult.Confirm("Are you sure?", intent);
        assertInstanceOf(GateResult.Confirm.class, result);
        assertEquals("Are you sure?", ((GateResult.Confirm) result).question());
        assertEquals(intent, ((GateResult.Confirm) result).intent());
    }

    @Test
    void exhaustive_switch() {
        GateResult result = new GateResult.Allow(new AgentIntent("test"));
        String outcome = switch (result) {
            case GateResult.Allow a -> "allow";
            case GateResult.Block b -> "block";
            case GateResult.Confirm c -> "confirm";
        };
        assertEquals("allow", outcome);
    }
}
