package rsp.app.posts.components;

import rsp.app.posts.entities.Post;
import rsp.app.posts.services.PostService;
import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.contract.QueryParam;

import java.util.List;
import java.util.Set;

public class PostsListContract extends ListViewContract<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final PostService postService;

    public PostsListContract(final Lookup lookup) {
        super(lookup);
        // Read service from lookup
        this.postService = lookup.get(PostService.class);
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
    public String sort() {
        return resolve(SORT);
    }

    @Override
    public List<Post> items() {
        // Get query params from context (populated by AutoAddressBarSyncComponent)
        int page = page();  // Uses resolve(PAGE) → reads from context "url.query.p"
        String sort = sort();  // Uses resolve(SORT) → reads from context "url.query.sort"

        // Call service directly (service comes from context)
        // Use pageSize from base class (configured via AppConfig)
        return postService.findAll(page, pageSize(), sort);
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
    protected Class<? extends ViewContract> createElementContract() {
        return PostCreateContract.class;
    }

    @Override
    protected Class<? extends ViewContract> editElementContract() {
        return PostEditContract.class;
    }
}
