package rsp.app.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.compositions.ui.DefaultEditView;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostEditBlock CRUD operations.
 * <p>
 * For create-mode tests, see PostCreateBlockTests.
 */
class PostEditBlockTests {

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService();
    }

    @Nested
    class DeleteTests {

        @Test
        void delete_removes_existing_post() {
            // Create a post
            final String id = postService.create(new Post(null, "Test", "Content"));
            assertTrue(postService.find(id).isPresent());

            // Delete via block
            final PostEditBlock block = new PostEditBlock(postService, new DefaultEditView());

            final boolean result = block.delete(id);

            assertTrue(result);
            assertFalse(postService.find(id).isPresent());
        }

        @Test
        void delete_returns_false_for_nonexistent_post() {
            final PostEditBlock block = new PostEditBlock(postService, new DefaultEditView());

            final boolean result = block.delete("99999");

            assertFalse(result);
        }
    }

    @Nested
    class ItemTests {

        @Test
        void item_returns_post_in_edit_mode() {
            final String id = postService.create(new Post(null, "Title", "Content"));
            final PostEditBlock block = new PostEditBlock(postService, new DefaultEditView());

            final Post post = block.item(id);

            assertNotNull(post);
            assertEquals(id, post.id());
            assertEquals("Title", post.title());
        }

        @Test
        void item_returns_null_for_nonexistent_post() {
            final PostEditBlock block = new PostEditBlock(postService, new DefaultEditView());

            assertNull(block.item("99999"));
        }
    }
}
