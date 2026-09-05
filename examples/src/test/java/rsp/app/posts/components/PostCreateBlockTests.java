package rsp.app.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.app.posts.services.PostService;
import rsp.compositions.ui.DefaultEditView;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostCreateBlock operations.
 */
class PostCreateBlockTests {

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService();
    }

    @Nested
    class SaveTests {

        @Test
        void save_creates_new_post() {
            final PostCreateBlock block = new PostCreateBlock(postService, new DefaultEditView());

            final Map<String, Object> fieldValues = Map.of(
                    "title", "Unique Test Post Title",
                    "content", "Post content"
            );

            final boolean result = block.save(fieldValues);

            assertTrue(result);
            // Verify post was created by finding it in the paginated results
            // PostService pre-populates with 25 posts, page is 1-based
            final var posts = postService.findAll(1, 100, "title");
            final var created = posts.stream()
                    .filter(p -> "Unique Test Post Title".equals(p.title()))
                    .findFirst();
            assertTrue(created.isPresent());
            assertEquals("Post content", created.get().content());
        }
    }

    @Nested
    class SchemaTests {

        @Test
        void schema_is_shared_with_the_grid_and_hides_the_server_owned_id_in_forms() {
            final PostCreateBlock block = new PostCreateBlock(postService, new DefaultEditView());

            final var schema = block.schema();

            assertSame(CrudSchemas.POSTS, schema);
            assertEquals(3, schema.columns().size());
            assertTrue(schema.field("id").isHidden());
            assertTrue(schema.columns().stream().anyMatch(c -> c.name().equals("title")));
            assertTrue(schema.columns().stream().anyMatch(c -> c.name().equals("content")));
        }
    }

}
