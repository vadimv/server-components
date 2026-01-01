package rsp.compositions.posts.components;

import rsp.compositions.ListViewContract;
import rsp.compositions.QueryParam;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

import java.util.List;

public class PostsListContract extends ListViewContract<Post> {
    private static final QueryParam<Integer> PAGE = new QueryParam<>("p", Integer.class, 1);
    private static final QueryParam<String> SORT = new QueryParam<>("sort", String.class, "asc");

    private final PostService postService;

    public PostsListContract(PostService postService) {
        this.postService = postService;
    }

    @Override
    public String name() {
        return "posts-list";
    }

    public int page() {
        return resolve(PAGE);
    }

    public String sort() {
        return resolve(SORT);
    }

    @Override
    public List<Post> items() {
        return postService.findAll(page(), 10, sort());
    }
}
