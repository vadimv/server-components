package rsp.app.posts.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.EventKey;
import rsp.compositions.agent.*;
import rsp.compositions.composition.StructureNode;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegexAgentServiceTests {

    private RegexAgentService agent;
    private StructureNode emptyTree;

    // Dummy event keys for test actions
    private static final EventKey.VoidKey CREATE_KEY = new EventKey.VoidKey("test.create");
    private static final EventKey.VoidKey SELECT_ALL_KEY = new EventKey.VoidKey("test.selectAll");
    @SuppressWarnings("unchecked")
    private static final EventKey.SimpleKey<Set<String>> DELETE_KEY =
        new EventKey.SimpleKey<>("test.delete", (Class<Set<String>>) (Class<?>) Set.class);
    private static final EventKey.SimpleKey<Integer> PAGE_KEY =
        new EventKey.SimpleKey<>("test.page", Integer.class);
    private static final EventKey.SimpleKey<String> EDIT_KEY =
        new EventKey.SimpleKey<>("test.edit", String.class);
    @SuppressWarnings("unchecked")
    private static final EventKey.SimpleKey<Map<String, Object>> SAVE_KEY =
        new EventKey.SimpleKey<>("test.save", (Class<Map<String, Object>>) (Class<?>) Map.class);

    private static final List<AgentAction> LIST_ACTIONS = List.of(
        new AgentAction("create", CREATE_KEY, "Create item", null),
        new AgentAction("delete", DELETE_KEY, "Delete items", "Set<String>: IDs",
            PayloadParsers.toSetOfStrings()),
        new AgentAction("edit", EDIT_KEY, "Edit item", "String: id"),
        new AgentAction("page", PAGE_KEY, "Go to page", "Integer: page number",
            PayloadParsers.toInteger()),
        new AgentAction("select_all", SELECT_ALL_KEY, "Select all", null)
    );

    private static final List<AgentAction> EDIT_ACTIONS = List.of(
        new AgentAction("save", SAVE_KEY, "Save entity", "Map<String, Object>: fields",
            PayloadParsers.toMapOfStringObject())
    );

    @BeforeEach
    void setUp() {
        agent = new RegexAgentService();
        emptyTree = new StructureNode(null, null, List.of(), List.of());
    }

    // --- Helpers to build profiles with structured metadata ---

    private static final List<Map<String, Object>> TEST_ITEMS = List.of(
        Map.of("id", 1, "title", "Post Title 1", "content", "Hello"),
        Map.of("id", 2, "title", "Post Title 2", "content", "World"),
        Map.of("id", 3, "title", "Post Title 3", "content", "Test")
    );

    private ContractProfile listProfileWithItems(List<Map<String, Object>> items) {
        ContractMetadata metadata = new ContractMetadata("Posts", "Paginated data list", null,
            Map.of("page", 1, "pageSize", 10, "sort", "asc", "items", items));
        return new ContractProfile(metadata, LIST_ACTIONS, MockListContract.class);
    }

    private ContractProfile editProfileWithEntity(Map<String, Object> entity) {
        ContractMetadata metadata = new ContractMetadata("Posts", "Form for editing an existing entity", null,
            Map.of("entity", entity));
        return new ContractProfile(metadata, EDIT_ACTIONS, MockEditContract.class);
    }

    // Marker classes for isList()/isEdit() checks
    static abstract class MockListContract extends ListViewContract<Object> {
        MockListContract() { super(null); }
    }
    static abstract class MockEditContract extends EditViewContract<Object> {
        MockEditContract() { super(null); }
    }

    @Nested
    class DeleteByName {

        @Test
        void deletes_by_exact_title() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete 'Post Title 1'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("delete", ar.action().action());
            // Payload is Array(String("1")) — parse via toSetOfStrings to verify
            assertEquals(Set.of("1"), PayloadParsers.toSetOfStrings().apply(ar.payload()));
        }

        @Test
        void deletes_by_title_with_double_quotes() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete \"Post Title 2\"", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals(Set.of("2"), PayloadParsers.toSetOfStrings().apply(ar.payload()));
        }

        @Test
        void deletes_by_title_without_quotes() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete Post Title 1", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("delete", ar.action().action());
            assertEquals(Set.of("1"), PayloadParsers.toSetOfStrings().apply(ar.payload()));
        }

        @Test
        void returns_text_reply_when_not_found() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete 'Nonexistent'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("not found"));
        }
    }

    @Nested
    class SearchFilter {

        @Test
        void searches_with_less_than() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id < 2", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("1 item"));
            assertTrue(message.contains("Post Title 1"));
        }

        @Test
        void searches_with_greater_than() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id > 1", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("2 item"));
        }

        @Test
        void returns_no_matches_message() {
            ContractProfile profile = listProfileWithItems(TEST_ITEMS);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id > 100", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("No items found"));
        }
    }

    @Nested
    class UpdateField {

        private final List<Map<String, Object>> SINGLE_ITEM = List.of(
            Map.of("id", 2, "title", "Post Title 2", "content", "World")
        );

        private final Map<String, Object> EDIT_ENTITY = Map.of(
            "id", "2", "title", "Post Title 2", "content", "World"
        );

        @Test
        void step1_emits_edit_action() {
            ContractProfile profile = listProfileWithItems(SINGLE_ITEM);
            AgentService.AgentResult result = agent.handlePrompt(
                "update post 2 adding 'test'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("edit", ar.action().action());
            assertEquals(AgentPayload.of("2"), ar.payload());
        }

        @Test
        void step2_emits_save_action_with_modified_content() {
            // Step 1: trigger update
            ContractProfile listP = listProfileWithItems(SINGLE_ITEM);
            agent.handlePrompt("update post 2 adding 'test'", listP, emptyTree);

            // Step 2: agent is called again with the edit contract now active
            ContractProfile editP = editProfileWithEntity(EDIT_ENTITY);
            AgentService.AgentResult result = agent.handlePrompt(
                "", editP, emptyTree);  // empty prompt — agent continues from state

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("save", ar.action().action());

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) PayloadParsers.toMapOfStringObject().apply(ar.payload());
            assertNotNull(payload);
            assertEquals("World test", payload.get("content"));
            assertEquals("Post Title 2", payload.get("title")); // unchanged
        }
    }

    @Nested
    class Navigation {

        @Test
        void navigates_to_matching_group() {
            StructureNode tree = new StructureNode("Root", null,
                List.of(
                    new StructureNode("Posts", "Blog posts",
                        List.of(), List.of(MockListContract.class))
                ),
                List.of());

            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "show Posts", profile, tree);

            assertInstanceOf(AgentService.AgentResult.NavigateResult.class, result);
            assertEquals(MockListContract.class,
                ((AgentService.AgentResult.NavigateResult) result).targetContract());
        }

        @Test
        void returns_text_reply_when_no_match() {
            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "show Unknown", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
        }
    }

    @Nested
    class Pagination {

        @Test
        void parses_page_command() {
            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "go to page 3", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("page", ar.action().action());
            assertEquals(AgentPayload.of(3), ar.payload());
        }

        @Test
        void parses_short_page_command() {
            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "page 5", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("page", ar.action().action());
            assertEquals(AgentPayload.of(5), ar.payload());
        }
    }

    @Nested
    class SelectAll {

        @Test
        void emits_select_all_action() {
            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "select all", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("select_all", ar.action().action());
        }
    }

    @Nested
    class EditSelected {

        @Test
        void returns_edit_action_with_empty_payload() {
            ContractProfile profile = listProfileWithItems(List.of());
            AgentService.AgentResult result = agent.handlePrompt(
                "edit selected", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.ActionResult.class, result);
            AgentService.AgentResult.ActionResult ar = (AgentService.AgentResult.ActionResult) result;
            assertEquals("edit", ar.action().action());
            assertEquals(AgentPayload.EMPTY, ar.payload());
        }
    }

    @Test
    void unrecognized_prompt_returns_text_reply() {
        ContractProfile profile = listProfileWithItems(List.of());
        AgentService.AgentResult result = agent.handlePrompt(
            "do something weird", profile, emptyTree);

        assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
    }
}
