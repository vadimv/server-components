package rsp.compositions.posts.components;

import rsp.compositions.ActionContract;
import rsp.compositions.Module;
import rsp.compositions.NotificationContract;
import rsp.compositions.Slot;
import rsp.compositions.ViewPlacement;
import rsp.compositions.posts.entities.Post;
import rsp.compositions.posts.services.PostService;

import java.util.Collections;
import java.util.List;

public class PostsModule implements Module {
    private final PostService postService;

    public PostsModule(PostService postService) {
        this.postService = postService;
    }

    @Override
    public String name() {
        return "posts";
    }

    @Override
    public List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.PRIMARY, new PostsListContract(this))
        );
    }

    @Override
    public List<NotificationContract> notifications() {
        return Collections.emptyList();
    }

    @Override
    public List<ActionContract> actions() {
        return Collections.emptyList();
    }

    /**
     * Fetch items from the service - exposes service functionality without passing service reference down.
     * This method is called by PostsListContract to retrieve posts.
     *
     * @param page the page number (1-indexed)
     * @param sort the sort order ("asc" or "desc")
     * @return list of posts for the specified page
     */
    public List<Post> fetchItems(int page, String sort) {
        return postService.findAll(page, 10, sort);
    }
}
