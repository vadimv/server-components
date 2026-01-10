package rsp.compositions.posts.components;

import rsp.component.ComponentContext;
import rsp.compositions.EditViewContract;
import rsp.compositions.ListSchema;
import rsp.compositions.PathParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

/**
 * PostEditContract - Contract for editing or creating a single post.
 * <p>
 * Reads the post ID from the URL path (e.g., /posts/1) and loads the corresponding post.
 * Supports create mode when the path contains the create token (e.g., /posts/new).
 * The schema is auto-derived from the Post record class.
 */
public class PostEditContract extends EditViewContract<Post> {
    private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);

    private final PostService postService;

    public PostEditContract(ComponentContext context) {
        super(context);
        // Read service from context
        this.postService = context.get(PostService.class);
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
    public ListSchema schema() {
        // Use class-based schema for create mode (no instance needed)
        // Use instance-based schema for edit mode (when entity exists)
        Post post = item();
        if (post != null) {
            return ListSchema.fromFirstItem(post);
        }
        // For create mode or not found, derive from class
        return ListSchema.fromRecordClass(Post.class);
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
}
