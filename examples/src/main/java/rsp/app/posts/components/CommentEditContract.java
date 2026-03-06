package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.PathParam;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;

import java.util.Map;
import java.util.Objects;

/**
 * CommentEditContract - Contract for editing an existing comment.
 */
public class CommentEditContract extends EditViewContract<Comment> {
    private static final PathParam<String> COMMENT_ID = new PathParam<>(1, String.class, null);

    private final CommentService commentService;

    public CommentEditContract(final Lookup lookup, final CommentService commentService) {
        super(lookup);
        this.commentService = Objects.requireNonNull(commentService);
    }

    @Override
    public String title() {
        return "Edit Comment";
    }

    @Override
    protected String resolveIdFromPath() {
        return resolve(COMMENT_ID);
    }

    @Override
    public Comment item() {
        String commentId = resolveId();
        if (commentId == null) {
            return null;
        }
        return commentService.find(commentId).orElse(null);
    }

    @Override
    public DataSchema schema() {
        return DataSchema.builder()
            .field("id", FieldType.ID)
                .hidden()
            .field("text", FieldType.TEXT)
                .label("Comment Text")
                .required()
                .widget(Widget.TEXTAREA)
            .field("postId", FieldType.STRING)
                .label("Post ID")
                .required()
            .build();
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false;
        }
        String text = (String) fieldValues.get("text");
        String postId = (String) fieldValues.get("postId");

        Comment comment = new Comment(id, text, postId);
        return commentService.update(id, comment);
    }

    @Override
    public boolean delete() {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false;
        }
        return commentService.delete(id);
    }
}
