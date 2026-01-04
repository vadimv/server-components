package rsp.compositions.posts.components;

import rsp.component.ComponentContext;
import rsp.compositions.EditViewContract;
import rsp.compositions.ListSchema;
import rsp.compositions.PathParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

/**
 * PostEditContract - Contract for editing a single post.
 * <p>
 * Reads the post ID from the URL path (e.g., /posts/1) and loads the corresponding post.
 * The schema is auto-derived from the Post record.
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
    public Post item() {
        // Extract post ID from URL path (e.g., /posts/123 → "123")
        String postId = resolve(POST_ID);

        if (postId == null) {
            return null;
        }

        // Load post from service
        return postService.find(postId).orElse(null);
    }

    @Override
    public ListSchema schema() {
        // Auto-derive schema from Post record
        Post post = item();
        return post != null ? ListSchema.fromFirstItem(post) : new ListSchema(java.util.List.of());
    }

    @Override
    public boolean save(java.util.Map<String, Object> fieldValues) {
        // Extract field values
        String id = (String) fieldValues.get("id");
        String title = (String) fieldValues.get("title");
        String content = (String) fieldValues.get("content");

        // Create Post from field values
        Post post = new Post(id, title, content);

        // Save via service
        if (id != null && !id.isEmpty()) {
            return postService.update(id, post);
        } else {
            postService.create(post);
            return true;
        }
    }
}
