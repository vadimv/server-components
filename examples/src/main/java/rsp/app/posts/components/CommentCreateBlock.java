package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.ComponentView;
import rsp.compositions.block.FormBlock;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.schema.DataSchema;
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
        return CrudSchemas.COMMENTS;
    }

    @Override
    protected boolean isCreateMode() {
        return true;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        return commentService.createResult(comment(fieldValues)).succeeded();
    }

    @Override
    protected FormMutationResult saveResult(Map<String, Object> fieldValues) {
        return commentService.createResult(comment(fieldValues));
    }

    private Comment comment(Map<String, Object> values) {
        return new Comment(null, (String) values.get("text"), (String) values.get("postId"));
    }
}
