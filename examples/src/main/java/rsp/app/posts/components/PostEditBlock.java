package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.block.EditBlock;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.block.PathParam;
import rsp.compositions.ui.EditView;

import java.util.Map;
import java.util.Objects;

/**
 * Block for editing an existing post.
 * <p>
 * If shown via SHOW event, receives the post ID via show data.
 * If routed via URL, loads the post by ID from the URL path (e.g., /posts/123).
 * <p>
 * For creating new posts, use {@link PostCreateBlock}.
 */
public class PostEditBlock extends EditBlock<Post> {
    private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);

    private final PostService postService;

    public PostEditBlock(PostService postService,
                            ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
        this.postService = Objects.requireNonNull(postService);
    }

    @Override
    public String title() {
        return "Edit Post";
    }

    @Override
    protected String resolveIdFromPath(Lookup lookup) {
        return POST_ID.resolve(lookup);
    }

    @Override
    public Post item(String postId) {
        if (postId == null) {
            return null;
        }
        return postService.find(postId).orElse(null);
    }

    @Override
    public DataSchema schema() {
        return CrudSchemas.POSTS;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false; // Cannot save without ID
        }
        return postService.updateResult(id, post(id, fieldValues)).succeeded();
    }

    @Override
    protected FormMutationResult saveResult(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isBlank()) return FormMutationResult.notFound("The post ID is missing.");
        return postService.updateResult(id, post(id, fieldValues));
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return postService.delete(id);
    }

    @Override
    protected FormMutationResult deleteResult(String id) {
        return postService.deleteResult(id);
    }

    private Post post(String id, Map<String, Object> values) {
        return new Post(id, (String) values.get("title"), (String) values.get("content"));
    }
}
