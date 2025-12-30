package rsp.examples.crud.components;

import rsp.compositions.ActionContract;
import rsp.compositions.Module;
import rsp.compositions.NotificationContract;
import rsp.compositions.Slot;
import rsp.compositions.ViewPlacement;
import rsp.examples.crud.services.PostService;

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
            new ViewPlacement(Slot.PRIMARY, new PostsListContract(postService))
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
}
