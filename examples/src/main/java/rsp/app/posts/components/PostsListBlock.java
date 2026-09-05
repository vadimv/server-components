package rsp.app.posts.components;

import rsp.compositions.block.Block;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
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

public class PostsListBlock extends ListBlock<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);

    private final PostService postService;

    public PostsListBlock(PostService postService,
                             ComponentView<ListView.ListViewState, ListView.ListIntent> view) {
        super(view);
        this.postService = Objects.requireNonNull(postService);
    }

    @Override
    protected QueryParam<Integer> pageQueryParam() {
        return PAGE;
    }

    @Override
    public String title() {
        return "Posts";
    }

    @Override
    protected DataSchema listSchema() {
        return CrudSchemas.POSTS;
    }

    @Override
    protected SortSpec defaultSort() {
        return new SortSpec("title", SortDirection.ASC);
    }

    @Override
    protected ListPage<Post> items(ListQuery query) {
        return postService.findAll(query);
    }

    @Override
    protected DeleteResult bulkDelete(Set<String> ids) {
        return postService.deleteAll(ids);
    }

    @Override
    protected Class<? extends Block<?, ?>> createElementBlock() {
        return PostCreateBlock.class;
    }

    @Override
    protected Class<? extends Block<?, ?>> editElementBlock() {
        return PostEditBlock.class;
    }
}
