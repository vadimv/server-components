package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.Lookup;
import rsp.compositions.DataSchema;
import rsp.compositions.EditViewContract;
import rsp.compositions.PathParam;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;

import java.util.Map;

/**
 * PostEditContract - Contract for editing an existing post.
 * <p>
 * Loads the post by ID from the URL path (e.g., /posts/123).
 * For creating new posts, use {@link PostCreateContract}.
 */
public class PostEditContract extends EditViewContract<Post> {
    private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);

    private final PostService postService;

    public PostEditContract(final Lookup lookup) {
        super(lookup);
        this.postService = lookup.get(PostService.class);
    }

    @Override
    protected String resolveId() {
        return resolve(POST_ID);
    }

    @Override
    public Post item() {
        String postId = resolveId();
        // Return null if no ID available (e.g., when pre-instantiated as overlay)
        if (postId == null) {
            return null;
        }
        return postService.find(postId).orElse(null);
    }

    @Override
    public DataSchema schema() {
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
    public boolean save(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false; // Cannot save without ID
        }
        String title = (String) fieldValues.get("title");
        String content = (String) fieldValues.get("content");

        Post post = new Post(id, title, content);
        return postService.update(id, post);
    }

    @Override
    public boolean delete() {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false;
        }
        return postService.delete(id);
    }
}
