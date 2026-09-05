package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.compositions.block.FormBlock;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.ui.EditView;

import java.util.Map;
import java.util.Objects;

/**
 * Block for creating a new post.
 * <p>
 * Focused block for entity creation. Unlike PostEditBlock:
 * <ul>
 *   <li>No entity loading - form starts empty</li>
 *   <li>No delete operation</li>
 *   <li>No ID resolution from URL</li>
 * </ul>
 * <p>
 * Uses the same schema as PostEditBlock but can be customized
 * if create form needs different fields (e.g., no ID field).
 */
public class PostCreateBlock extends FormBlock<Post> {

    private final PostService postService;

    public PostCreateBlock(PostService postService,
                              ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
        this.postService = Objects.requireNonNull(postService);
    }

    @Override
    public String title() {
        return "Create Post";
    }

    @Override
    public DataSchema schema() {
        return CrudSchemas.POSTS;
    }

    @Override
    protected boolean isCreateMode() {
        return true;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        return postService.createResult(post(fieldValues)).succeeded();
    }

    @Override
    protected FormMutationResult saveResult(Map<String, Object> fieldValues) {
        return postService.createResult(post(fieldValues));
    }

    private Post post(Map<String, Object> fieldValues) {
        return new Post(null, (String) fieldValues.get("title"), (String) fieldValues.get("content"));
    }
}
