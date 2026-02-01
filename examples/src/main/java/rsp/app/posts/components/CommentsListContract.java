package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.contract.ActionBindings;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.QueryParam;

import java.util.List;
import java.util.Set;

public class CommentsListContract extends ListViewContract<Comment> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final CommentService commentService;

    public CommentsListContract(final Lookup lookup) {
        super(lookup);
        this.commentService = lookup.get(CommentService.class);
    }

    @Override
    public Object typeHint() {
        return Comment.class;
    }

    @Override
    public String title() {
        return "Comments";
    }

    @Override
    public int page() {
        return resolve(PAGE);
    }

    @Override
    public String sort() {
        return resolve(SORT);
    }

    @Override
    public List<Comment> items() {
        int page = page();
        String sort = sort();
        return commentService.findAll(page, pageSize(), sort);
    }

    @Override
    protected DataSchema customizeSchema(DataSchema schema) {
        return schema.withSelectable(true);
    }

    @Override
    protected int bulkDelete(Set<String> ids) {
        return commentService.bulkDelete(ids);
    }

    @Override
    protected ActionBindings actionBindings() {
        return ActionBindings.builder()
            .bind("edit", CommentEditContract.class)
            .bind("create", CommentCreateContract.class)
            .build();
    }
}
