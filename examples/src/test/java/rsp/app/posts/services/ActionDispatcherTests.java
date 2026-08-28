package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.app.posts.components.TestLookup;
import rsp.component.Lookup;
import rsp.compositions.block.BlockAction;
import rsp.compositions.block.BlockActionPayload;
import rsp.compositions.agent.GateResult;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.ActionDispatcher.DispatchResult;
import rsp.compositions.agent.ActionGate;
import rsp.compositions.block.PayloadSchema;
import rsp.compositions.block.EventKeys;
import rsp.compositions.block.BlockRuntime;
import rsp.compositions.block.Block;
import rsp.compositions.block.ListBlockEvents;
import rsp.util.json.JsonDataType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTests {

    private final ActionDispatcher dispatcher = new ActionDispatcher();
    private final ActionGate allowAll = new AllowAllGate();

    // Declared actions for the stub block
    private static final BlockAction CREATE_ACTION = new BlockAction("create",
        ListBlockEvents.CREATE_ELEMENT_REQUESTED, "Open create form");
    private static final BlockAction EDIT_ACTION = new BlockAction("edit",
        ListBlockEvents.EDIT_ELEMENT_REQUESTED, "Open edit form",
        new PayloadSchema.StringValue("row ID"));
    private static final BlockAction DELETE_ACTION = new BlockAction("delete",
        ListBlockEvents.BULK_DELETE_REQUESTED, "Delete items",
        new PayloadSchema.StringSet("row IDs"));
    private static final BlockAction PAGE_ACTION = new BlockAction("page",
        ListBlockEvents.PAGE_CHANGE_REQUESTED, "Navigate to page",
        new PayloadSchema.IntegerValue("page number"));
    private static final BlockAction SELECT_ALL_ACTION = new BlockAction("select_all",
        ListBlockEvents.SELECT_ALL_REQUESTED, "Select all rows");

    /**
     * Stub block that declares standard list actions for testing.
     */
    static class StubListBlock extends Block<Object, Object> {
        private final Lookup lookup;

        StubListBlock(Lookup lookup) {
            this.lookup = lookup;
        }

        @Override
        public Lookup lookup() {
            return lookup;
        }

        @Override
        public rsp.component.ComponentStateSupplier<Object> initStateSupplier() {
            return (_, _) -> new Object();
        }

        @Override
        public rsp.component.ComponentView<Object, Object> componentView() {
            return _ -> _ -> rsp.dsl.Html.text("");
        }

        @Override
        public List<BlockAction> agentActions() {
            return List.of(CREATE_ACTION, EDIT_ACTION, DELETE_ACTION, PAGE_ACTION, SELECT_ALL_ACTION);
        }

        @Override
        public String title() { return "Stub"; }
    }

    @Test
    void navigate_publishes_set_primary() {
        TestLookup lookup = new TestLookup();

        dispatcher.dispatchNavigate(StubListBlock.class, lookup);

        assertTrue(lookup.wasPublished(EventKeys.SET_PRIMARY));
    }

    @Test
    void page_publishes_page_change_requested() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        DispatchResult result = dispatcher.dispatch(PAGE_ACTION, BlockActionPayload.of(3), block, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.PAGE_CHANGE_REQUESTED));
        assertEquals(3, (int) lookup.getLastPublishedPayload(ListBlockEvents.PAGE_CHANGE_REQUESTED));
    }

    @Test
    void select_all_publishes_event() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        DispatchResult result = dispatcher.dispatch(SELECT_ALL_ACTION, BlockActionPayload.EMPTY, block, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.SELECT_ALL_REQUESTED));
    }

    @Test
    void edit_with_payload_publishes_edit_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        DispatchResult result = dispatcher.dispatch(EDIT_ACTION, BlockActionPayload.of("42"), block, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.EDIT_ELEMENT_REQUESTED));
        assertEquals("42", lookup.getLastPublishedPayload(ListBlockEvents.EDIT_ELEMENT_REQUESTED));
    }

    @Test
    void create_publishes_create_element_requested() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        DispatchResult result = dispatcher.dispatch(CREATE_ACTION, BlockActionPayload.EMPTY, block, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.CREATE_ELEMENT_REQUESTED));
    }

    @Test
    void delete_publishes_bulk_delete_requested() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        BlockActionPayload deletePayload = new BlockActionPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, block, lookup, allowAll);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.BULK_DELETE_REQUESTED));
    }

    @Test
    void block_gate_returns_blocked() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);
        ActionGate blockGate = (a, p, l) -> new GateResult.Block("Not allowed");

        BlockActionPayload deletePayload = new BlockActionPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, block, lookup, blockGate);

        assertInstanceOf(DispatchResult.Blocked.class, result);
        assertEquals("Not allowed", ((DispatchResult.Blocked) result).reason());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void confirm_gate_returns_awaiting_confirmation() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);
        ActionGate confirmGate = (a, p, l) -> new GateResult.Confirm("Sure?", a, p);

        BlockActionPayload deletePayload = new BlockActionPayload(
            new JsonDataType.Array(new JsonDataType.String("1")));
        DispatchResult result = dispatcher.dispatch(DELETE_ACTION, deletePayload, block, lookup, confirmGate);

        assertInstanceOf(DispatchResult.AwaitingConfirmation.class, result);
        assertEquals("Sure?", ((DispatchResult.AwaitingConfirmation) result).question());
        assertTrue(lookup.getPublishedEvents().isEmpty());
    }

    @Test
    void dispatchDirect_bypasses_gate() {
        TestLookup lookup = new TestLookup();
        StubListBlock block = new StubListBlock(lookup);

        DispatchResult result = dispatcher.dispatchDirect(SELECT_ALL_ACTION, BlockActionPayload.EMPTY, block);

        assertInstanceOf(DispatchResult.Dispatched.class, result);
        assertTrue(lookup.wasPublished(ListBlockEvents.SELECT_ALL_REQUESTED));
    }
}
