package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.compositions.block.FormBlock;
import rsp.compositions.block.FormMutationResult;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldChoice;
import rsp.compositions.schema.FieldDef;
import rsp.compositions.ui.EditView;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Block for creating a new comment.
 */
public class CommentCreateBlock extends FormBlock<Comment> {

    private final CommentService commentService;
    private final PostService postService;

    public CommentCreateBlock(final CommentService commentService,
                              final PostService postService,
                              ComponentView<EditView.EditViewState, EditView.EditIntent> view) {
        super(view);
        this.commentService = Objects.requireNonNull(commentService);
        this.postService = Objects.requireNonNull(postService);
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

    @Override
    protected List<FieldChoice> fieldChoices(FieldDef field, Lookup lookup) {
        if (!"postId".equals(field.name())) return super.fieldChoices(field, lookup);
        return postService.findAllForSelection().stream()
                .map(post -> new FieldChoice(post.id(), post.title()))
                .toList();
    }

    private Comment comment(Map<String, Object> values) {
        return new Comment(null, (String) values.get("text"), (String) values.get("postId"));
    }
}
