package rsp.app.posts.components;

import rsp.compositions.schema.DataSchema;
import rsp.compositions.schema.FieldType;
import rsp.compositions.schema.TextAlign;
import rsp.compositions.schema.Validators;
import rsp.compositions.schema.Widget;

/** Shared field metadata for the posts/comments grids and create/edit forms. */
public final class CrudSchemas {
    private CrudSchemas() {
    }

    public static final DataSchema POSTS = DataSchema.builder()
            .field("id", FieldType.ID).label("ID")
            .field("title", FieldType.STRING)
                .label("Title")
                .required()
                .maxLength(200)
                .placeholder("Enter post title…")
            .field("content", FieldType.TEXT)
                .label("Content")
                .widget(Widget.TEXTAREA)
                .maxLength(10_000)
                .placeholder("Write your post content here…")
            .column("id").sortable().width("6rem").align(TextAlign.RIGHT)
            .column("title").sortable().filterable().width("30%")
            .column("content").filterable().width("auto")
            .build()
            .withSelectable(true);

    public static final DataSchema COMMENTS = DataSchema.builder()
            .field("id", FieldType.ID).label("ID")
            .field("text", FieldType.TEXT)
                .label("Comment")
                .required()
                .maxLength(1_000)
                .widget(Widget.TEXTAREA)
                .placeholder("Enter comment…")
            .field("postId", FieldType.STRING)
                .label("Post ID")
                .required()
                .validate(Validators.pattern("[1-9][0-9]*"))
                .placeholder("Post ID this comment belongs to")
            .column("id").sortable().width("6rem").align(TextAlign.RIGHT)
            .column("text").sortable().filterable().width("auto")
            .column("postId").sortable().filterable().width("8rem").align(TextAlign.RIGHT)
            .build()
            .withSelectable(true);
}
