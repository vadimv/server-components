package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.ComponentView;
import rsp.compositions.block.FormBlock;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;
import rsp.compositions.ui.EditView;

import java.util.Map;
import java.util.Objects;

/**
 * Block for creating a new comment.
 */
public class CommentCreateBlock extends FormBlock<Comment> {

    private final CommentService commentService;

    public CommentCreateBlock(final CommentService commentService,
                                 ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
        this.commentService = Objects.requireNonNull(commentService);
    }

    @Override
    public String title() {
        return "Create Comment";
    }

    @Override
    public DataSchema schema() {
        return DataSchema.builder()
            .field("text", FieldType.TEXT)
                .label("Comment Text")
                .required()
                .widget(Widget.TEXTAREA)
                .placeholder("Enter comment...")
            .field("postId", FieldType.STRING)
                .label("Post ID")
                .required()
                .placeholder("Post ID this comment belongs to")
            .build();
    }

    @Override
    protected boolean isCreateMode() {
        return true;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        String text = (String) fieldValues.get("text");
        String postId = (String) fieldValues.get("postId");

        Comment comment = new Comment(null, text, postId);
        commentService.create(comment);
        return true;
    }
}
