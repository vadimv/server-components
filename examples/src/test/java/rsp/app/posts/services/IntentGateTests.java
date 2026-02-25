package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentGate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IntentGateTests {

    @Test
    void allowAllGate_always_allows() {
        AllowAllGate gate = new AllowAllGate();
        AgentIntent intent = new AgentIntent("delete");
        GateResult result = gate.evaluate(intent, new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
        assertEquals(intent, ((GateResult.Allow) result).intent());
    }

    @Test
    void compositeGate_first_block_wins() {
        IntentGate allowGate = (i, l) -> new GateResult.Allow(i);
        IntentGate blockGate = (i, l) -> new GateResult.Block("Denied");
        IntentGate neverReached = (i, l) -> { throw new AssertionError("Should not be called"); };

        CompositeGate composite = new CompositeGate(List.of(allowGate, blockGate, neverReached));
        GateResult result = composite.evaluate(new AgentIntent("test"), new TestLookup());

        assertInstanceOf(GateResult.Block.class, result);
        assertEquals("Denied", ((GateResult.Block) result).reason());
    }

    @Test
    void compositeGate_first_confirm_wins() {
        IntentGate allowGate = (i, l) -> new GateResult.Allow(i);
        IntentGate confirmGate = (i, l) -> new GateResult.Confirm("Sure?", i);

        CompositeGate composite = new CompositeGate(List.of(allowGate, confirmGate));
        GateResult result = composite.evaluate(new AgentIntent("test"), new TestLookup());

        assertInstanceOf(GateResult.Confirm.class, result);
    }

    @Test
    void compositeGate_all_allow_results_in_allow() {
        IntentGate allow1 = (i, l) -> new GateResult.Allow(i);
        IntentGate allow2 = (i, l) -> new GateResult.Allow(i);

        CompositeGate composite = new CompositeGate(List.of(allow1, allow2));
        GateResult result = composite.evaluate(new AgentIntent("test"), new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
    }

    @Test
    void compositeGate_empty_rules_allows() {
        CompositeGate composite = new CompositeGate(List.of());
        GateResult result = composite.evaluate(new AgentIntent("test"), new TestLookup());

        assertInstanceOf(GateResult.Allow.class, result);
    }
}
