package rsp.compositions.posts.components;

import rsp.component.ComponentContext;
import rsp.compositions.ListViewContract;
import rsp.compositions.Module;
import rsp.compositions.QueryParam;
import rsp.compositions.posts.entities.Post;

import java.util.List;

public class PostsListContract extends ListViewContract<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final Module module;

    public PostsListContract(ComponentContext context, Module module) {
        super(context);
        this.module = module;
    }

    @Override
    public String name() {
        return "posts-list";
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

        // Call module to fetch data from service
        return ((PostsModule) module).fetchItems(page, sort);
    }
}
