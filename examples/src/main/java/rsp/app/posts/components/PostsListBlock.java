package rsp.app.posts.components;

import rsp.compositions.block.Block;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.ComponentView;
import rsp.component.Lookup;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.block.ListBlock;
import rsp.compositions.block.ListView;
import rsp.compositions.block.QueryParam;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PostsListBlock extends ListBlock<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

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
    protected String sort(Lookup lookup) {
        return SORT.resolve(lookup);
    }

    @Override
    protected List<Post> items(int page, int pageSize, String sort) {
        return postService.findAll(page, pageSize, sort);
    }

    @Override
    protected DataSchema customizeSchema(DataSchema schema) {
        // Enable row selection for bulk operations
        return schema.withSelectable(true);
    }

    @Override
    protected int bulkDelete(Set<String> ids) {
        return postService.bulkDelete(ids);
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
