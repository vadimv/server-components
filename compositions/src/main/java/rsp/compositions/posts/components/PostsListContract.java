package rsp.compositions.posts.components;

import rsp.compositions.ListViewContract;
import rsp.compositions.QueryParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

import java.util.List;

public class PostsListContract extends ListViewContract<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");


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


}
