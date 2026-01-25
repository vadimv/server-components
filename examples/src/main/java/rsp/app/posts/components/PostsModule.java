package rsp.app.posts.components;

import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.ViewPlacement;

import java.util.List;

/**
 * PostsModule - Declares the posts domain's view placements.
 * <p>
 * Uses Slot to determine rendering behavior:
 * <ul>
 *   <li>{@link PostsListContract} - List view (PRIMARY slot, full page)</li>
 *   <li>{@link PostCreateContract} - Create form (OVERLAY slot, popup)</li>
 *   <li>{@link PostEditContract} - Edit form (OVERLAY slot, popup)</li>
 * </ul>
 * <p>
 * Slot.OVERLAY means these contracts are shown as popups/modals,
 * triggered by component events, with no URL change.
 */
public class PostsModule implements Composition {

    @Override
    public List<ViewPlacement> views() {
        return List.of(
            new ViewPlacement(Slot.PRIMARY, PostsListContract.class, PostsListContract::new),
            new ViewPlacement(Slot.OVERLAY, PostCreateContract.class, PostCreateContract::new),
            new ViewPlacement(Slot.OVERLAY, PostEditContract.class, PostEditContract::new)
        );
    }
}
