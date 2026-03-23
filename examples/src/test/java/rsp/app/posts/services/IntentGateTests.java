package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.component.EventKey;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionGate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntentGateTests {

    private static final AgentAction TEST_ACTION = new AgentAction("test",
        new EventKey.VoidKey("test.action"), "Test action", null);

    @Test
    void allowAllGate_always_allows() {
        AllowAllGate gate = new AllowAllGate();
        GateResult result = gate.evaluate(TEST_ACTION, null, new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(TEST_ACTION, ((GateResult.Allow) result).action());
    }

    @Test
    void compositeGate_first_block_wins() {
        ActionGate allowGate = (a, p, l) -> new GateResult.Allow(a, p);
        ActionGate blockGate = (a, p, l) -> new GateResult.Block("Denied");
        ActionGate neverReached = (a, p, l) -> { throw new AssertionError("Should not be called"); };

        CompositeGate composite = new CompositeGate(List.of(allowGate, blockGate, neverReached));
        GateResult result = composite.evaluate(TEST_ACTION, null, new TestLookup());

        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Denied", ((GateResult.Block) result).reason());
    }

    @Test
    void compositeGate_first_confirm_wins() {
        ActionGate allowGate = (a, p, l) -> new GateResult.Allow(a, p);
        ActionGate confirmGate = (a, p, l) -> new GateResult.Confirm("Sure?", a, p);

        CompositeGate composite = new CompositeGate(List.of(allowGate, confirmGate));
        GateResult result = composite.evaluate(TEST_ACTION, null, new TestLookup());

        assertInstanceOf(GateResult.Confirm.class, result);
    }

    @Test
    void compositeGate_all_allow_results_in_allow() {
        ActionGate allow1 = (a, p, l) -> new GateResult.Allow(a, p);
        ActionGate allow2 = (a, p, l) -> new GateResult.Allow(a, p);

        CompositeGate composite = new CompositeGate(List.of(allow1, allow2));
        GateResult result = composite.evaluate(TEST_ACTION, null, new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
    }

    @Test
    void compositeGate_empty_rules_allows() {
        CompositeGate composite = new CompositeGate(List.of());
        GateResult result = composite.evaluate(TEST_ACTION, null, new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
    }
}
