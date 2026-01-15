package rsp.compositions.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.CommandsEnqueue;
import rsp.component.ComponentContext;
import rsp.component.ComponentEventEntry;
import rsp.component.Subscriber;
import rsp.compositions.ContextKeys;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;
import rsp.dom.DomEventEntry;
import rsp.page.EventContext;
import rsp.page.events.Command;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostEditContract CRUD operations.
 */
class PostEditContractTests {

    private PostService postService;

    // No-op Subscriber for tests
    static class NoOpSubscriber implements Subscriber {
        @Override
        public void addWindowEventHandler(String eventType, Consumer<EventContext> eventHandler,
                                          boolean preventDefault, DomEventEntry.Modifier modifier) {}

        @Override
        public void addComponentEventHandler(String eventType,
                                             Consumer<ComponentEventEntry.EventContext> eventHandler,
                                             boolean preventDefault) {}
    }

    // No-op CommandsEnqueue for tests
    static class NoOpCommandsEnqueue implements CommandsEnqueue {
        @Override
        public void offer(Command command) {}
    }

    @BeforeEach
    void setUp() {
        postService = new PostService();
    }

    private ComponentContext contextWithPathId(final String id) {
        return new ComponentContext()
                .with(PostService.class, postService)
                .with(ContextKeys.URL_PATH.with("1"), id)
                .with(ContextKeys.ROUTE_PATTERN, "/posts/:id")
                .with(CommandsEnqueue.class, new NoOpCommandsEnqueue())
                .with(Subscriber.class, new NoOpSubscriber());
    }

    @Nested
    class DeleteTests {

        @Test
        void delete_removes_existing_post() {
            // Create a post
            final String id = postService.create(new Post(null, "Test", "Content"));
            assertTrue(postService.find(id).isPresent());

            // Delete via contract
            final ComponentContext context = contextWithPathId(id);
            final PostEditContract contract = new PostEditContract(context);

            final boolean result = contract.delete();

            assertTrue(result);
            assertFalse(postService.find(id).isPresent());
        }

        @Test
        void delete_returns_false_for_nonexistent_post() {
            final ComponentContext context = contextWithPathId("99999");
            final PostEditContract contract = new PostEditContract(context);

            final boolean result = contract.delete();

            assertFalse(result);
        }

        @Test
        void delete_throws_in_create_mode_with_new_token() {
            final ComponentContext context = contextWithPathId("new");
            final PostEditContract contract = new PostEditContract(context);

            assertThrows(IllegalStateException.class, contract::delete);
        }
    }

    @Nested
    class ItemTests {

        @Test
        void item_returns_post_in_edit_mode() {
            final String id = postService.create(new Post(null, "Title", "Content"));
            final ComponentContext context = contextWithPathId(id);
            final PostEditContract contract = new PostEditContract(context);

            final Post post = contract.item();

            assertNotNull(post);
            assertEquals(id, post.id());
            assertEquals("Title", post.title());
        }

        @Test
        void item_returns_null_in_create_mode() {
            final ComponentContext context = contextWithPathId("new");
            final PostEditContract contract = new PostEditContract(context);

            assertNull(contract.item());
        }

        @Test
        void item_returns_null_for_nonexistent_post() {
            final ComponentContext context = contextWithPathId("99999");
            final PostEditContract contract = new PostEditContract(context);

            assertNull(contract.item());
        }
    }

    @Nested
    class CreateModeTests {

        @Test
        void is_create_mode_true_with_new_token() {
            final ComponentContext context = contextWithPathId("new");
            final PostEditContract contract = new PostEditContract(context);

            assertTrue(contract.isCreateMode());
        }

        @Test
        void is_create_mode_false_with_id() {
            final ComponentContext context = contextWithPathId("123");
            final PostEditContract contract = new PostEditContract(context);

            assertFalse(contract.isCreateMode());
        }
    }
}
