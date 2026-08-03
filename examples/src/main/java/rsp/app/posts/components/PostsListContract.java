package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
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

public class PostsListContract extends ListContractComponent<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final PostService postService;

    public PostsListContract(PostService postService,
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
    protected Class<? extends Contract> createElementContract() {
        return PostCreateContract.class;
    }

    @Override
    protected Class<? extends Contract> editElementContract() {
        return PostEditContract.class;
    }
}
