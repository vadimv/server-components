package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.IntentGate;
import rsp.compositions.contract.EventKeys;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.ViewContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class IntentDispatcherTests {

    private final IntentDispatcher dispatcher = new IntentDispatcher(new PostService());
    private final IntentGate allowAll = new AllowAllGate();

    static abstract class StubContract extends ViewContract {
        protected StubContract(rsp.component.Lookup l) { super(l); }
    }

    @Test
    void navigate_publishes_set_primary() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("navigate", Map.of(), StubContract.class);
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertTrue(lookup.wasPublished(EventKeys.SET_PRIMARY));
        assertFalse(replies.isEmpty());
    }

    @Test
    void page_publishes_page_change_requested() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("page", Map.of("page", 3));
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertTrue(lookup.wasPublished(ListViewContract.PAGE_CHANGE_REQUESTED));
        assertEquals(3, (int) lookup.getLastPublishedPayload(ListViewContract.PAGE_CHANGE_REQUESTED));
        assertTrue(replies.get(0).contains("page 3"));
    }

    @Test
    void select_all_publishes_event() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("select_all");
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertTrue(lookup.wasPublished(ListViewContract.SELECT_ALL_REQUESTED));
    }

    @Test
    void edit_with_id_publishes_edit_element_requested() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("edit", Map.of("id", "42"));
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertTrue(lookup.wasPublished(ListViewContract.EDIT_ELEMENT_REQUESTED));
        assertEquals("42", lookup.getLastPublishedPayload(ListViewContract.EDIT_ELEMENT_REQUESTED));
    }

    @Test
    void edit_without_id_replies_no_selection() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("edit");
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertFalse(lookup.wasPublished(ListViewContract.EDIT_ELEMENT_REQUESTED));
        assertTrue(replies.get(0).contains("No item selected"));
    }

    @Test
    void create_publishes_create_element_requested() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();

        AgentIntent intent = new AgentIntent("create");
        dispatcher.dispatch(intent, lookup, allowAll, replies::add, _ -> {});

        assertTrue(lookup.wasPublished(ListViewContract.CREATE_ELEMENT_REQUESTED));
    }

    @Test
    void block_gate_sends_reason_no_event() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();
        IntentGate blockGate = (intent, l) -> new GateResult.Block("Not allowed");

        AgentIntent intent = new AgentIntent("navigate", Map.of(), StubContract.class);
        dispatcher.dispatch(intent, lookup, blockGate, replies::add, _ -> {});

        assertTrue(lookup.getPublishedEvents().isEmpty());
        assertEquals("Not allowed", replies.get(0));
    }

    @Test
    void confirm_gate_sends_question_and_stores_pending() {
        TestLookup lookup = new TestLookup();
        List<String> replies = new ArrayList<>();
        AtomicReference<AgentIntent> pending = new AtomicReference<>();
        IntentGate confirmGate = (intent, l) -> new GateResult.Confirm("Sure?", intent);

        AgentIntent intent = new AgentIntent("delete");
        dispatcher.dispatch(intent, lookup, confirmGate, replies::add, pending::set);

        assertTrue(lookup.getPublishedEvents().isEmpty());
        assertEquals("Sure?", replies.get(0));
        assertNotNull(pending.get());
        assertEquals("delete", pending.get().action());
    }
}
