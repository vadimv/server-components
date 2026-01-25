package rsp.app.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostEditContract CRUD operations.
 * <p>
 * For create-mode tests, see PostCreateContractTests.
 */
class PostEditContractTests {

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService();
    }

    private Lookup lookupWithPathId(final String id) {
        return new TestLookup()
                .withData(PostService.class, postService)
                .withData(ContextKeys.URL_PATH.with("1"), id)
                .withData(ContextKeys.ROUTE_PATTERN, "/posts/:id");
    }

    @Nested
    class DeleteTests {

        @Test
        void delete_removes_existing_post() {
            // Create a post
            final String id = postService.create(new Post(null, "Test", "Content"));
            assertTrue(postService.find(id).isPresent());

            // Delete via contract
            final Lookup lookup = lookupWithPathId(id);
            final PostEditContract contract = new PostEditContract(lookup);

            final boolean result = contract.delete();

            assertTrue(result);
            assertFalse(postService.find(id).isPresent());
        }

        @Test
        void delete_returns_false_for_nonexistent_post() {
            final Lookup lookup = lookupWithPathId("99999");
            final PostEditContract contract = new PostEditContract(lookup);

            final boolean result = contract.delete();

            assertFalse(result);
        }
    }

    @Nested
    class ItemTests {

        @Test
        void item_returns_post_in_edit_mode() {
            final String id = postService.create(new Post(null, "Title", "Content"));
            final Lookup lookup = lookupWithPathId(id);
            final PostEditContract contract = new PostEditContract(lookup);

            final Post post = contract.item();

            assertNotNull(post);
            assertEquals(id, post.id());
            assertEquals("Title", post.title());
        }

        @Test
        void item_returns_null_for_nonexistent_post() {
            final Lookup lookup = lookupWithPathId("99999");
            final PostEditContract contract = new PostEditContract(lookup);

            assertNull(contract.item());
        }
    }

    @Nested
    class EditModeTests {

        @Test
        void is_create_mode_always_false() {
            final Lookup lookup = lookupWithPathId("123");
            final PostEditContract contract = new PostEditContract(lookup);

            // EditViewContract always returns false for isCreateMode
            assertFalse(contract.isCreateMode());
        }
    }
}
