package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.contract.EditContractComponent;
import rsp.compositions.contract.PathParam;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
import rsp.compositions.ui.EditView;

import java.util.Map;
import java.util.Objects;

/**
 * PostEditContract - Contract for editing an existing post.
 * <p>
 * If shown via SHOW event, receives the post ID via show data.
 * If routed via URL, loads the post by ID from the URL path (e.g., /posts/123).
 * <p>
 * For creating new posts, use {@link PostCreateContract}.
 */
public class PostEditContract extends EditContractComponent<Post> {
    private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);

    private final PostService postService;

    public PostEditContract(PostService postService,
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
    public boolean delete(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return postService.delete(id);
    }
}
