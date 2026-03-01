package rsp.compositions.agent;

import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.application.TestLookup;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentActionFilterTests {

    private static final AgentAction CREATE = new AgentAction("create",
        new EventKey.VoidKey("test.create"), "Create item", null);
    private static final AgentAction EDIT = new AgentAction("edit",
        new EventKey.SimpleKey<>("test.edit", String.class), "Edit item", "String: id");
    private static final AgentAction DELETE = new AgentAction("delete",
        new EventKey.SimpleKey<>("test.delete", Set.class), "Delete items", "Set<String>: ids");
    private static final AgentAction PAGE = new AgentAction("page",
        new EventKey.SimpleKey<>("test.page", Integer.class), "Navigate to page", "Integer: page");
    private static final AgentAction SELECT_ALL = new AgentAction("select_all",
        new EventKey.VoidKey("test.selectAll"), "Select all", null);

    private static final List<AgentAction> ALL_ACTIONS = List.of(CREATE, EDIT, DELETE, PAGE, SELECT_ALL);

    @Test
    void readOnlyFilter_keeps_only_page_and_select_all() {
        ReadOnlyFilter filter = new ReadOnlyFilter();
        List<AgentAction> filtered = filter.filter(ALL_ACTIONS, new TestLookup());

        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().anyMatch(a -> "page".equals(a.action())));
        assertTrue(filtered.stream().anyMatch(a -> "select_all".equals(a.action())));
    }

    @Test
    void readOnlyFilter_removes_mutating_actions() {
        ReadOnlyFilter filter = new ReadOnlyFilter();
        List<AgentAction> filtered = filter.filter(ALL_ACTIONS, new TestLookup());

        assertFalse(filtered.stream().anyMatch(a -> "create".equals(a.action())));
        assertFalse(filtered.stream().anyMatch(a -> "edit".equals(a.action())));
        assertFalse(filtered.stream().anyMatch(a -> "delete".equals(a.action())));
    }

    @Test
    void readOnlyFilter_handles_empty_list() {
        ReadOnlyFilter filter = new ReadOnlyFilter();
        List<AgentAction> filtered = filter.filter(List.of(), new TestLookup());

        assertTrue(filtered.isEmpty());
    }

    @Test
    void readOnlyFilter_handles_list_with_no_matching_actions() {
        ReadOnlyFilter filter = new ReadOnlyFilter();
        List<AgentAction> filtered = filter.filter(List.of(CREATE, DELETE), new TestLookup());

        assertTrue(filtered.isEmpty());
    }

    @Test
    void custom_filter_can_restrict_by_action_name() {
        AgentActionFilter noDeleteFilter = (actions, ctx) -> actions.stream()
            .filter(a -> !"delete".equals(a.action()))
            .toList();

        List<AgentAction> filtered = noDeleteFilter.filter(ALL_ACTIONS, new TestLookup());

        assertEquals(4, filtered.size());
        assertFalse(filtered.stream().anyMatch(a -> "delete".equals(a.action())));
    }
}
