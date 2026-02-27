package rsp.compositions.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.compositions.composition.StructureNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceTests {

    private AgentService agent;
    private StructureNode emptyTree;

    @BeforeEach
    void setUp() {
        agent = new AgentService();
        emptyTree = new StructureNode(null, null, List.of(), List.of());
    }

    // --- Helper to build a list contract profile ---
    private ContractProfile listProfile(String description) {
        return new ContractProfile(description, List.of(), MockListContract.class);
    }

    private ContractProfile editProfile(String description) {
        return new ContractProfile(description, List.of(), MockEditContract.class);
    }

    // Marker classes for isList()/isEdit() checks
    static abstract class MockListContract extends rsp.compositions.contract.ListViewContract<Object> {
        MockListContract() { super(null); }
    }
    static abstract class MockEditContract extends rsp.compositions.contract.EditViewContract<Object> {
        MockEditContract() { super(null); }
    }

    @Nested
    class DeleteByName {

        private final String LIST_DESCRIPTION =
            "Displays a list of Posts.\n" +
            "Current page: 1, sort: asc\n" +
            "Items on page: 3, page size: 10\n" +
            "Items:\n" +
            "  Post[id=1, title=Post Title 1, content=Hello]\n" +
            "  Post[id=2, title=Post Title 2, content=World]\n" +
            "  Post[id=3, title=Post Title 3, content=Test]\n" +
            "Schema: id:ID, title:STRING, content:TEXT";

        @Test
        void deletes_by_exact_title() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete 'Post Title 1'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("delete", intent.action());
            assertEquals(Set.of("1"), intent.params().get("payload"));
        }

        @Test
        void deletes_by_title_with_double_quotes() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete \"Post Title 2\"", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals(Set.of("2"), intent.params().get("payload"));
        }

        @Test
        void deletes_by_title_without_quotes() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete Post Title 1", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("delete", intent.action());
            assertEquals(Set.of("1"), intent.params().get("payload"));
        }

        @Test
        void returns_text_reply_when_not_found() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "delete 'Nonexistent'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("not found"));
        }
    }

    @Nested
    class SearchFilter {

        private final String LIST_DESCRIPTION =
            "Displays a list of Posts.\n" +
            "Current page: 1, sort: asc\n" +
            "Items on page: 3, page size: 10\n" +
            "Items:\n" +
            "  Post[id=1, title=Post Title 1, content=Hello]\n" +
            "  Post[id=2, title=Post Title 2, content=World]\n" +
            "  Post[id=3, title=Post Title 3, content=Test]\n" +
            "Schema: id:ID, title:STRING, content:TEXT";

        @Test
        void searches_with_less_than() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id < 2", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("1 item"));
            assertTrue(message.contains("Post Title 1"));
        }

        @Test
        void searches_with_greater_than() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id > 1", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("2 item"));
        }

        @Test
        void returns_no_matches_message() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "search all posts with id > 100", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("No items found"));
        }
    }

    @Nested
    class UpdateField {

        private final String LIST_DESCRIPTION =
            "Displays a list of Posts.\n" +
            "Items:\n" +
            "  Post[id=2, title=Post Title 2, content=World]\n" +
            "Schema: id:ID, title:STRING, content:TEXT";

        private final String EDIT_DESCRIPTION =
            "Edit form for Posts.\n" +
            "Entity: Post[id=2, title=Post Title 2, content=World]\n" +
            "Fields: id:ID, title:STRING, content:TEXT";

        @Test
        void step1_emits_edit_intent() {
            ContractProfile profile = listProfile(LIST_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "update post 2 adding 'test'", profile, emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("edit", intent.action());
            assertEquals("2", intent.params().get("payload"));
        }

        @Test
        void step2_emits_save_intent_with_modified_content() {
            // Step 1: trigger update
            ContractProfile listP = listProfile(LIST_DESCRIPTION);
            agent.handlePrompt("update post 2 adding 'test'", listP, emptyTree);

            // Step 2: agent is called again with the edit contract now active
            ContractProfile editP = editProfile(EDIT_DESCRIPTION);
            AgentService.AgentResult result = agent.handlePrompt(
                "", editP, emptyTree);  // empty prompt — agent continues from state

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("save", intent.action());

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) intent.params().get("payload");
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

            ContractProfile profile = listProfile("some description");
            AgentService.AgentResult result = agent.handlePrompt(
                "show Posts", profile, tree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("navigate", intent.action());
            assertEquals(MockListContract.class, intent.targetContract());
        }

        @Test
        void returns_text_reply_when_no_match() {
            AgentService.AgentResult result = agent.handlePrompt(
                "show Unknown", listProfile("desc"), emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
        }
    }

    @Nested
    class Pagination {

        @Test
        void parses_page_command() {
            AgentService.AgentResult result = agent.handlePrompt(
                "go to page 3", listProfile("desc"), emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("page", intent.action());
            assertEquals(3, intent.params().get("payload"));
        }

        @Test
        void parses_short_page_command() {
            AgentService.AgentResult result = agent.handlePrompt(
                "page 5", listProfile("desc"), emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("page", intent.action());
            assertEquals(5, intent.params().get("payload"));
        }
    }

    @Nested
    class SelectAll {

        @Test
        void emits_select_all_intent() {
            AgentService.AgentResult result = agent.handlePrompt(
                "select all", listProfile("desc"), emptyTree);

            assertInstanceOf(AgentService.AgentResult.IntentResult.class, result);
            AgentIntent intent = ((AgentService.AgentResult.IntentResult) result).intent();
            assertEquals("select_all", intent.action());
        }
    }

    @Nested
    class EditSelected {

        @Test
        void returns_text_reply_for_stub() {
            AgentService.AgentResult result = agent.handlePrompt(
                "edit selected", listProfile("desc"), emptyTree);

            assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
            String message = ((AgentService.AgentResult.TextReply) result).message();
            assertTrue(message.contains("row ID"));
        }
    }

    @Nested
    class ParseItems {

        @Test
        void parses_items_from_description() {
            String description =
                "Displays a list of Posts.\n" +
                "Items:\n" +
                "  Post[id=1, title=Hello, content=World]\n" +
                "  Post[id=2, title=Bye, content=Moon]\n" +
                "Schema: id:ID";

            List<Map<String, String>> items = agent.parseItems(description);
            assertEquals(2, items.size());
            assertEquals("1", items.get(0).get("id"));
            assertEquals("Hello", items.get(0).get("title"));
            assertEquals("2", items.get(1).get("id"));
        }

        @Test
        void returns_empty_for_null() {
            assertTrue(agent.parseItems(null).isEmpty());
        }

        @Test
        void returns_empty_for_no_items_section() {
            assertTrue(agent.parseItems("Just a description").isEmpty());
        }
    }

    @Nested
    class ParseEntityFields {

        @Test
        void parses_entity_from_edit_description() {
            String description =
                "Edit form for Posts.\n" +
                "Entity: Post[id=2, title=Post Title 2, content=World]\n" +
                "Fields: id:ID, title:STRING, content:TEXT";

            Map<String, Object> fields = agent.parseEntityFields(description);
            assertEquals("2", fields.get("id"));
            assertEquals("Post Title 2", fields.get("title"));
            assertEquals("World", fields.get("content"));
        }

        @Test
        void returns_empty_for_no_entity() {
            assertTrue(agent.parseEntityFields("No entity here").isEmpty());
        }
    }

    @Test
    void unrecognized_prompt_returns_text_reply() {
        AgentService.AgentResult result = agent.handlePrompt(
            "do something weird", listProfile("desc"), emptyTree);

        assertInstanceOf(AgentService.AgentResult.TextReply.class, result);
    }
}
