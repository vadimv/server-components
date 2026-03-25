package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.agent.AgentAction;
import rsp.compositions.agent.AgentPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.ActionDispatcher.DispatchResult;
import rsp.compositions.agent.ActionGate;
import rsp.compositions.agent.PayloadParsers;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTests {

    private final ActionDispatcher dispatcher = new ActionDispatcher();
    private final ActionGate allowAll = new AllowAllGate();

    // Declared actions for the stub contract
    private static final AgentAction CREATE_ACTION = new AgentAction("create",
        ListViewContract.CREATE_ELEMENT_REQUESTED, "Open create form", null);
    private static final AgentAction EDIT_ACTION = new AgentAction("edit",
        ListViewContract.EDIT_ELEMENT_REQUESTED, "Open edit form", "String: row ID",
        PayloadParsers.toStringPayload());
    private static final AgentAction DELETE_ACTION = new AgentAction("delete",
        ListViewContract.BULK_DELETE_REQUESTED, "Delete items", "Set<String>: row IDs",
        PayloadParsers.toSetOfStrings());
    private static final AgentAction PAGE_ACTION = new AgentAction("page",
        ListViewContract.PAGE_CHANGE_REQUESTED, "Navigate to page", "Integer: page number",
        PayloadParsers.toInteger());
    private static final AgentAction SELECT_ALL_ACTION = new AgentAction("select_all",
        ListViewContract.SELECT_ALL_REQUESTED, "Select all rows", null);

    /**
     * Stub contract that declares standard list actions for testing.
     */
    static class StubListContract extends ViewContract {
        StubListContract(Lookup lookup) { super(lookup); }

        @Override
        public List<AgentAction> agentActions() {
            return List.of(CREATE_ACTION, EDIT_ACTION, DELETE_ACTION, PAGE_ACTION, SELECT_ALL_ACTION);
        }

        @Override
        public ComponentContext enrichContext(ComponentContext context) { return context; }

        @Override
        public String title() { return "Stub"; }
    }

    @Test
    void navigate_publishes_set_primary() {
        TestLookup lookup = new TestLookup();

        dispatcher.dispatchNavigate(StubListContract.class, lookup);

        assertTrue(lookup.wasPublished(EventKeys.SET_PRIMARY));
    }

    @Test
    void page_publishes_page_change_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        DispatchResult result = dispatcher.dispatch(PAGE_ACTION, AgentPayload.of(3), contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.PAGE_CHANGE_REQUESTED));
        assertEquals(3, (int) lookup.getLastPublishedPayload(ListViewContract.PAGE_CHANGE_REQUESTED));
    }

    @Test
    void select_all_publishes_event() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        DispatchResult result = dispatcher.dispatch(SELECT_ALL_ACTION, AgentPayload.EMPTY, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.SELECT_ALL_REQUESTED));
    }

    @Test
    void edit_with_payload_publishes_edit_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        DispatchResult result = dispatcher.dispatch(EDIT_ACTION, AgentPayload.of("42"), contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.EDIT_ELEMENT_REQUESTED));
        assertEquals("42", lookup.getLastPublishedPayload(ListViewContract.EDIT_ELEMENT_REQUESTED));
    }

    @Test
    void create_publishes_create_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        DispatchResult result = dispatcher.dispatch(CREATE_ACTION, AgentPayload.EMPTY, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.CREATE_ELEMENT_REQUESTED));
    }

    @Test
    void delete_publishes_bulk_delete_requested() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        AgentPayload deletePayload = new AgentPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, contract, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.BULK_DELETE_REQUESTED));
    }

    @Test
    void block_gate_returns_blocked() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);
        ActionGate blockGate = (a, p, l) -> new GateResult.Block("Not allowed");

        AgentPayload deletePayload = new AgentPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, contract, lookup, blockGate);

        assertInstanceOf(DispatchResult.Blocked.class, result);
        assertEquals("Not allowed", ((DispatchResult.Blocked) result).reason());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void confirm_gate_returns_awaiting_confirmation() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);
        ActionGate confirmGate = (a, p, l) -> new GateResult.Confirm("Sure?", a, p);

        AgentPayload deletePayload = new AgentPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, contract, lookup, confirmGate);

        assertInstanceOf(DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Sure?", ((DispatchResult.AwaitingConfirmation) result).question());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void dispatchDirect_bypasses_gate() {
        TestLookup lookup = new TestLookup();
        StubListContract contract = new StubListContract(lookup);

        DispatchResult result = dispatcher.dispatchDirect(SELECT_ALL_ACTION, AgentPayload.EMPTY, contract);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListViewContract.SELECT_ALL_REQUESTED));
    }
}
