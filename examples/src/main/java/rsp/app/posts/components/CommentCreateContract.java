package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.Lookup;
import rsp.compositions.contract.CreateViewContract;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.Widget;

import java.util.Map;

/**
 * CommentCreateContract - Contract for creating a new comment.
 */
public class CommentCreateContract extends CreateViewContract<Comment> {

    private final CommentService commentService;

    public CommentCreateContract(final Lookup lookup) {
        super(lookup);
        this.commentService = lookup.get(CommentService.class);
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
    public boolean save(Map<String, Object> fieldValues) {
        String text = (String) fieldValues.get("text");
        String postId = (String) fieldValues.get("postId");

        Comment comment = new Comment(null, text, postId);
        commentService.create(comment);
        return true;
    }
}
