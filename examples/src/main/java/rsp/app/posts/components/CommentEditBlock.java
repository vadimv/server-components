package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
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
 * Block for editing an existing comment.
 */
public class CommentEditBlock extends EditBlock<Comment> {
    private static final PathParam<String> COMMENT_ID = new PathParam<>(1, String.class, null);

    private final CommentService commentService;

    public CommentEditBlock(final CommentService commentService,
                               ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
        this.commentService = Objects.requireNonNull(commentService);
    }

    @Override
    public String title() {
        return "Edit Comment";
    }

    @Override
    protected String resolveIdFromPath(Lookup lookup) {
        return COMMENT_ID.resolve(lookup);
    }

    @Override
    public Comment item(String commentId) {
        if (commentId == null) {
            return null;
        }
        return commentService.find(commentId).orElse(null);
    }

    @Override
    public DataSchema schema() {
        return CrudSchemas.COMMENTS;
    }

    @Override
    public boolean save(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isEmpty()) {
            return false;
        }
        return commentService.updateResult(id, comment(id, fieldValues)).succeeded();
    }

    @Override
    protected FormMutationResult saveResult(Map<String, Object> fieldValues) {
        String id = resolveId();
        if (id == null || id.isBlank()) return FormMutationResult.notFound("The comment ID is missing.");
        return commentService.updateResult(id, comment(id, fieldValues));
    }

    @Override
    public boolean delete(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return commentService.delete(id);
    }

    @Override
    protected FormMutationResult deleteResult(String id) {
        return commentService.deleteResult(id);
    }

    private Comment comment(String id, Map<String, Object> values) {
        return new Comment(id, (String) values.get("text"), (String) values.get("postId"));
    }
}
