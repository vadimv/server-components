package rsp.app.posts.services;

import org.junit.jupiter.api.Test;
import rsp.compositions.agent.AgentIntent;
import rsp.compositions.contract.NavigationEntry;
import rsp.compositions.contract.ViewContract;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentServiceTests {

    private final AgentService agentService = new AgentService();

    // Stub contract classes for navigation entries
    static abstract class PostsListContract extends ViewContract {
        protected PostsListContract(rsp.component.Lookup l) { super(l); }
    }
    static abstract class CommentsListContract extends ViewContract {
        protected CommentsListContract(rsp.component.Lookup l) { super(l); }
    }

    private final List<NavigationEntry> entries = List.of(
            new NavigationEntry("posts", "Posts", PostsListContract.class, "/posts"),
            new NavigationEntry("comments", "Comments", CommentsListContract.class, "/comments")
    );

    @Test
    void navigate_show_posts() {
        AgentIntent intent = agentService.handlePrompt("show posts", entries);
        assertNotNull(intent);
        assertEquals("navigate", intent.action());
        assertEquals(PostsListContract.class, intent.targetContract());
    }

    @Test
    void navigate_go_to_comments() {
        AgentIntent intent = agentService.handlePrompt("go to comments", entries);
        assertNotNull(intent);
        assertEquals("navigate", intent.action());
        assertEquals(CommentsListContract.class, intent.targetContract());
    }

    @Test
    void navigate_label_only() {
        AgentIntent intent = agentService.handlePrompt("posts", entries);
        assertNotNull(intent);
        assertEquals("navigate", intent.action());
    }

    @Test
    void pagination_page_3() {
        AgentIntent intent = agentService.handlePrompt("page 3", entries);
        assertNotNull(intent);
        assertEquals("page", intent.action());
        assertEquals(3, intent.params().get("page"));
    }

    @Test
    void pagination_go_to_page() {
        AgentIntent intent = agentService.handlePrompt("go to page 5", entries);
        assertNotNull(intent);
        assertEquals("page", intent.action());
        assertEquals(5, intent.params().get("page"));
    }

    @Test
    void select_all() {
        AgentIntent intent = agentService.handlePrompt("select all", entries);
        assertNotNull(intent);
        assertEquals("select_all", intent.action());
    }

    @Test
    void edit_selected() {
        AgentIntent intent = agentService.handlePrompt("edit selected", entries);
        assertNotNull(intent);
        assertEquals("edit", intent.action());
    }

    @Test
    void create() {
        AgentIntent intent = agentService.handlePrompt("create", entries);
        assertNotNull(intent);
        assertEquals("create", intent.action());
    }

    @Test
    void delete_with_single_quotes() {
        AgentIntent intent = agentService.handlePrompt("delete 'Post Title 1'", entries);
        assertNotNull(intent);
        assertEquals("delete", intent.action());
        assertEquals("Post Title 1", intent.params().get("name"));
    }

    @Test
    void delete_with_double_quotes() {
        AgentIntent intent = agentService.handlePrompt("delete \"Post Title 2\"", entries);
        assertNotNull(intent);
        assertEquals("delete", intent.action());
        assertEquals("Post Title 2", intent.params().get("name"));
    }

    @Test
    void delete_without_quotes() {
        AgentIntent intent = agentService.handlePrompt("delete Post Title 1", entries);
        assertNotNull(intent);
        assertEquals("delete", intent.action());
        assertEquals("Post Title 1", intent.params().get("name"));
    }

    @Test
    void unknown_returns_null() {
        AgentIntent intent = agentService.handlePrompt("hello world", entries);
        assertNull(intent);
    }

    @Test
    void null_prompt_returns_null() {
        assertNull(agentService.handlePrompt(null, entries));
    }

    @Test
    void blank_prompt_returns_null() {
        assertNull(agentService.handlePrompt("   ", entries));
    }

    @Test
    void case_insensitive() {
        AgentIntent intent = agentService.handlePrompt("Show Posts", entries);
        assertNotNull(intent);
        assertEquals("navigate", intent.action());
    }
}
