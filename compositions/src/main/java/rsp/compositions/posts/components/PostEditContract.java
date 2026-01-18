package rsp.compositions.posts.components;

import rsp.component.Lookup;
import rsp.compositions.EditViewContract;
import rsp.compositions.DataSchema;
import rsp.compositions.PathParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;

/**
 * PostEditContract - Contract for editing or creating a single post.
 * <p>
 * Reads the post ID from the URL path (e.g., /posts/1) and loads the corresponding post.
 * Supports create mode when the path contains the create token (e.g., /posts/new).
 * <p>
 * Uses the Schema DSL to define field configuration with validation and UI hints.
 */
public class PostEditContract extends EditViewContract<Post> {
    private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);

    private final PostService postService;

    public PostEditContract(final Lookup lookup) {
        super(lookup);
        // Read service from lookup
        this.postService = lookup.get(PostService.class);
    }

    @Override
    protected String resolveId() {
        // Extract post ID from URL path (e.g., /posts/123 → "123", /posts/new → "new")
        return resolve(POST_ID);
    }

    @Override
    public Post item() {
        // In create mode, return null (empty form)
        if (isCreateMode()) {
            return null;
        }

        // Load post from service
        String postId = resolveId();
        return postService.find(postId).orElse(null);
    }

    @Override
    public DataSchema schema() {
        // Use Schema DSL for explicit field configuration
        return DataSchema.builder()
            .field("id", FieldType.ID)
                .hidden()
            .field("title", FieldType.STRING)
                .label("Post Title")
                .required()
                .maxLength(200)
                .placeholder("Enter post title...")
            .field("content", FieldType.TEXT)
                .label("Content")
                .widget(Widget.TEXTAREA)
                .placeholder("Write your post content here...")
            .build();
    }

    @Override
    public boolean save(java.util.Map<String, Object> fieldValues) {
        // Extract field values
        String id = (String) fieldValues.get("id");
        String title = (String) fieldValues.get("title");
        String content = (String) fieldValues.get("content");

        // Create Post from field values
        Post post = new Post(id, title, content);

        // Create or update based on mode
        if (isCreateMode() || id == null || id.isEmpty()) {
            postService.create(post);
            return true;
        } else {
            return postService.update(id, post);
        }
    }

    @Override
    public boolean delete() {
        if (isCreateMode()) {
            throw new IllegalStateException("Cannot delete in create mode");
        }

        final String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false;
        }

        return postService.delete(id);
    }
}
