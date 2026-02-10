package rsp.app.posts.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import rsp.component.Lookup;
import rsp.compositions.contract.ContextKeys;
import rsp.app.posts.services.PostService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PostCreateContract operations.
 */
class PostCreateContractTests {

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService();
    }

    private Lookup createLookup() {
        return new TestLookup()
                .withData(PostService.class, postService)
                .withData(ContextKeys.ROUTE_PATTERN, "/posts/new");
    }

    @Nested
    class SaveTests {

        @Test
        void save_creates_new_post() {
            final Lookup lookup = createLookup();
            final PostCreateContract contract = new PostCreateContract(lookup);

            final Map<String, Object> fieldValues = Map.of(
                    "title", "Unique Test Post Title",
                    "content", "Post content"
            );

            final boolean result = contract.save(fieldValues);

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
        void schema_has_title_and_content_fields() {
            final Lookup lookup = createLookup();
            final PostCreateContract contract = new PostCreateContract(lookup);

            final var schema = contract.schema();

            // Should have title and content (no id field for create)
            assertEquals(2, schema.columns().size());
            assertTrue(schema.columns().stream().anyMatch(c -> c.name().equals("title")));
            assertTrue(schema.columns().stream().anyMatch(c -> c.name().equals("content")));
        }
    }

    @Nested
    class ListRouteTests {

        @Test
        void list_route_returns_posts_path() {
            final Lookup lookup = createLookup();
            final PostCreateContract contract = new PostCreateContract(lookup);

            assertEquals("/posts", contract.listRoute());
        }
    }
}
