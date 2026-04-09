package rsp.compositions.agent;

import rsp.compositions.contract.ContractActionPayload;


import rsp.compositions.contract.ContractAction;


import org.junit.jupiter.api.Test;
import rsp.component.EventKey;

import static org.junit.jupiter.api.Assertions.*;

class GateResultTests {

    private static final ContractAction TEST_ACTION =
        new ContractAction("delete", new EventKey.VoidKey("test.delete"), "Delete items");

    @Test
    void allow_carries_action_and_payload() {
        ContractActionPayload payload = ContractActionPayload.of("payload-value");
        GateResult result = new GateResult.Allow(TEST_ACTION, payload);
        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(TEST_ACTION, ((GateResult.Allow) result).action());
        assertEquals(payload, ((GateResult.Allow) result).payload());
    }

    @Test
    void block_carries_reason() {
        GateResult result = new GateResult.Block("Not permitted");
        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Not permitted", ((GateResult.Block) result).reason());
    }

    @Test
    void confirm_carries_question_action_and_payload() {
        GateResult result = new GateResult.Confirm("Are you sure?", TEST_ACTION, ContractActionPayload.EMPTY);
        assertInstanceOf(GateResult.Confirm.class, result);
        assertEquals("Are you sure?", ((GateResult.Confirm) result).question());
        assertEquals(TEST_ACTION, ((GateResult.Confirm) result).action());
        assertEquals(ContractActionPayload.EMPTY, ((GateResult.Confirm) result).payload());
    }

    @Test
    void exhaustive_switch() {
        GateResult result = new GateResult.Allow(TEST_ACTION, ContractActionPayload.EMPTY);
        String outcome = switch (result) {
            case GateResult.Allow a -> "allow";
            case GateResult.Block b -> "block";
            case GateResult.Confirm c -> "confirm";
        };
        assertEquals("allow", outcome);
    }
}
