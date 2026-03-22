package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentDispatcher;
import rsp.compositions.agent.IntentDispatcher.DispatchResult;
import rsp.compositions.agent.IntentGate;
import rsp.compositions.agent.PayloadParsers;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IntentDispatcherTests {

    private final IntentDispatcher dispatcher = new IntentDispatcher();
    private final IntentGate allowAll = new AllowAllGate();

    /**
     * Stub contract that declares standard list actions for testing.
     */
    static class StubListContract extends ViewContract {
        StubListContract(Lookup lookup) { super(lookup); }

        @Override
        public List<AgentAction> agentActions() {
            return List.of(
                new AgentAction("create", ListViewContract.CREATE_ELEMENT_REQUESTED,
                    "Open create form", null),
                new AgentAction("edit", ListViewContract.EDIT_ELEMENT_REQUESTED,
                    "Open edit form", "String: row ID",
                    PayloadParsers.toStringPayload()),
                new AgentAction("delete", ListViewContract.BULK_DELETE_REQUESTED,
                    "Delete items", "Set<String>: row IDs",
                    PayloadParsers.toSetOfStrings()),
                new AgentAction("page", ListViewContract.PAGE_CHANGE_REQUESTED,
                    "Navigate to page", "Integer: page number",
                    PayloadParsers.toInteger()),
                new AgentAction("select_all", ListViewContract.SELECT_ALL_REQUESTED,
                    "Select all rows", null)
            );
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) { return context; }

        @Override
        public String title() { return "Stub"; }
    }

    @Test
    void navigate_publishes_set_primary() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("navigate", Map.of(), StubListContract.class);
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(EventKeys.SET_PRIMARY));
    }

    @Test
    void page_publishes_page_change_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("page", Map.of("payload", 3));
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.PAGE_CHANGE_REQUESTED));
        assertEquals(3, (int) lookup.getLastPublishedPayload(ListViewContract.PAGE_CHANGE_REQUESTED));
    }

    @Test
    void select_all_publishes_event() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("select_all");
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.SELECT_ALL_REQUESTED));
    }

    @Test
    void edit_with_payload_publishes_edit_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("edit", Map.of("payload", "42"));
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.EDIT_ELEMENT_REQUESTED));
        assertEquals("42", lookup.getLastPublishedPayload(ListViewContract.EDIT_ELEMENT_REQUESTED));
    }

    @Test
    void create_publishes_create_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("create");
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.CREATE_ELEMENT_REQUESTED));
    }

    @Test
    void delete_publishes_bulk_delete_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("delete", Map.of("payload", Set.of("1")));
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.BULK_DELETE_REQUESTED));
    }

    @Test
    void unknown_action_returns_unknown() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("nonexistent");
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.UnknownAction.class, result);
        assertEquals("nonexistent", ((DispatchResult.UnknownAction) result).action());
    }

    @Test
    void block_gate_returns_blocked() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);
        IntentGate blockGate = (intent, l) -> new GateResult.Block("Not allowed");

        AgentIntent intent = new AgentIntent("navigate", Map.of(), StubListContract.class);
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, blockGate);

        assertInstanceOf(DispatchResult.Blocked.class, result);
        assertEquals("Not allowed", ((DispatchResult.Blocked) result).reason());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void confirm_gate_returns_awaiting_confirmation() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);
        IntentGate confirmGate = (intent, l) -> new GateResult.Confirm("Sure?", intent);

        AgentIntent intent = new AgentIntent("delete", Map.of("payload", Set.of("1")));
        DispatchResult result = dispatcher.dispatch(intent, contract, lookup, confirmGate);

        assertInstanceOf(DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Sure?", ((DispatchResult.AwaitingConfirmation) result).question());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void dispatchDirect_bypasses_gate() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentIntent intent = new AgentIntent("select_all");
        DispatchResult result = dispatcher.dispatchDirect(intent, contract, lookup);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.SELECT_ALL_REQUESTED));
    }
}
