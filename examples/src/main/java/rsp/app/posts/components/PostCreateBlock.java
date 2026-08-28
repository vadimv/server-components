package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.compositions.block.FormBlock;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
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
        // Create form schema - no ID field needed
        return DataSchema.builder()
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
    protected boolean isCreateMode() {
        return true;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        String title = (String) fieldValues.get("title");
        String content = (String) fieldValues.get("content");

        // Create new post with null ID (service will assign)
        Post post = new Post(null, title, content);
        postService.create(post);
        return true;
    }
}
