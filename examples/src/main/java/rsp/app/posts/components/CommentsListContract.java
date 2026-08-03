package rsp.app.posts.components;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.compositions.contract.Contract;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.contract.ListContractComponent;
import rsp.compositions.contract.ListView;
import rsp.compositions.contract.QueryParam;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CommentsListContract extends ListContractComponent<Comment> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final CommentService commentService;

    public CommentsListContract(final CommentService commentService,
                                ComponentView<ListView.ListViewState, ListView.ListIntent> view) {
        super(view);
        this.commentService = Objects.requireNonNull(commentService);
    }

    @Override
    public QueryParam<Integer> pageQueryParam() {
        return PAGE;
    }

    @Override
    public String title() {
        return "Comments";
    }

    @Override
    protected String sort(Lookup lookup) {
        return SORT.resolve(lookup);
    }

    @Override
    protected List<Comment> items(int page, int pageSize, String sort) {
        return commentService.findAll(page, pageSize, sort);
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
    protected Class<? extends Contract> createElementContract() {
        return CommentCreateContract.class;
    }

    @Override
    protected Class<? extends Contract> editElementContract() {
        return CommentEditContract.class;
    }
}
