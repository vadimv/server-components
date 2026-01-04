package rsp.compositions.posts.components;

import rsp.component.ComponentContext;
import rsp.compositions.ListViewContract;
import rsp.compositions.QueryParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

import java.util.List;

public class PostsListContract extends ListViewContract<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final PostService postService;

    public PostsListContract(ComponentContext context) {
        super(context);
        // Read service from context
        this.postService = context.get(PostService.class);
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
    public List<Post> items() {
        // Get query params from context (populated by AutoAddressBarSyncComponent)
        int page = page();  // Uses resolve(PAGE) → reads from context "url.query.p"
        String sort = sort();  // Uses resolve(SORT) → reads from context "url.query.sort"

        // Call service directly (service comes from context)
        return postService.findAll(page, 10, sort);
    }
}
