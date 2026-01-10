package rsp.compositions.posts.components;

import rsp.compositions.*;
import rsp.compositions.Module;

import java.util.Collections;
import java.util.List;

/**
 * PostsModule - Pure orchestrator for posts domain.
 * <p>
 * Declares which contracts exist in the posts domain.
 * Does NOT expose services - contracts access services directly from context.
 */
public class PostsModule implements Module {

    @Override
    public EditMode editMode() {
        return EditMode.QUERY_PARAM;
    }

    @Override
    public List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.PRIMARY, PostsListContract.class, PostsListContract::new),
            new ViewPlacement(Slot.PRIMARY, PostEditContract.class, PostEditContract::new)
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
