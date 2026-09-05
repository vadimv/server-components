package rsp.app.posts.components;

import rsp.compositions.block.Block;

import rsp.app.posts.entities.Comment;
import rsp.app.posts.services.CommentService;
import rsp.component.ComponentView;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.block.ListBlock;
import rsp.compositions.block.DeleteResult;
import rsp.compositions.block.ListPage;
import rsp.compositions.block.ListQuery;
import rsp.compositions.block.ListView;
import rsp.compositions.block.QueryParam;
import rsp.compositions.block.SortDirection;
import rsp.compositions.block.SortSpec;

import java.util.Objects;
import java.util.Set;

public class CommentsListBlock extends ListBlock<Comment> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);

    private final CommentService commentService;

    public CommentsListBlock(final CommentService commentService,
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
    protected DataSchema listSchema() {
        return CrudSchemas.COMMENTS;
    }

    @Override
    protected SortSpec defaultSort() {
        return new SortSpec("text", SortDirection.ASC);
    }

    @Override
    protected ListPage<Comment> items(ListQuery query) {
        return commentService.findAll(query);
    }

    @Override
    protected DeleteResult bulkDelete(Set<String> ids) {
        return commentService.deleteAll(ids);
    }

    @Override
    protected Class<? extends Block<?, ?>> createElementBlock() {
        return CommentCreateBlock.class;
    }

    @Override
    protected Class<? extends Block<?, ?>> editElementBlock() {
        return CommentEditBlock.class;
    }
}
