package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;

import static rsp.compositions.contract.EventKeys.DELETE_REQUESTED;
import static rsp.compositions.contract.EventKeys.MODAL_DELETE_SUCCESS;
import static rsp.compositions.contract.EventKeys.OPEN_EDIT_MODAL;

/**
 * EditViewContract - Base contract for editing existing entities.
 * <p>
 * Provides functionality for loading and modifying existing entities:
 * <ul>
 *   <li>Entity loading via {@link #item()}</li>
 *   <li>Entity deletion via {@link #delete()}</li>
 *   <li>ID resolution from URL path</li>
 * </ul>
 * <p>
 * For creating new entities, use {@link CreateViewContract} instead.
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class PostEditContract extends EditViewContract<Post> {
 *     private static final PathParam<String> POST_ID = new PathParam<>(1, String.class, null);
 *     private final PostService postService;
 *
 *     public PostEditContract(Lookup lookup) {
 *         super(lookup);
 *         this.postService = lookup.get(PostService.class);
 *     }
 *
 *     @Override
 *     protected String resolveId() {
 *         return resolve(POST_ID);
 *     }
 *
 *     @Override
 *     public Post item() {
 *         return postService.find(resolveId()).orElse(null);
 *     }
 *
 *     @Override
 *     public DataSchema schema() {
 *         return DataSchema.builder()
 *             .field("id", FieldType.ID).hidden()
 *             .field("title", FieldType.STRING).required()
 *             .field("content", FieldType.TEXT)
 *             .build();
 *     }
 *
 *     @Override
 *     public boolean save(Map<String, Object> fieldValues) {
 *         String id = resolveId();
 *         Post post = new Post(id,
 *             (String) fieldValues.get("title"),
 *             (String) fieldValues.get("content"));
 *         return postService.update(id, post);
 *     }
 *
 *     @Override
 *     public boolean delete() {
 *         return postService.delete(resolveId());
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type of entity being edited
 */
public abstract class EditViewContract<T> extends FormViewContract<T> {

    /**
     * Entity ID received via OPEN_EDIT_MODAL event (for overlay mode).
     * Null when not in overlay mode or before event received.
     */
    private String overlayEntityId = null;

    protected EditViewContract(final Lookup lookup) {
        super(lookup);

        // In overlay mode, listen for the entity ID and activate
        if (isModalMode) {
            lookup.subscribe(OPEN_EDIT_MODAL, (eventName, entityId) -> {
                this.overlayEntityId = entityId;
                setActive();  // Mark this overlay as active for event handling
            });

            // For auto-opened overlays (Case 2: OVERLAY + route), activate immediately
            Boolean isAutoOpen = lookup.get(ContextKeys.IS_AUTO_OPEN_OVERLAY);
            if (isAutoOpen != null && isAutoOpen) {
                setActive();
            }
        }

        // Handle delete request - only if this is the active overlay
        lookup.subscribe(DELETE_REQUESTED, () -> {
            if (shouldHandleEvent()) {
                handleDeleteRequested(isModalMode);
            }
        });
    }

    /**
     * Get the entity ID received via OPEN_EDIT_MODAL event.
     * Use this in overlay mode instead of path parameter resolution.
     *
     * @return The entity ID, or null if not in overlay mode or event not received
     */
    protected String getOverlayEntityId() {
        return overlayEntityId;
    }

    /**
     * Always returns false - this is an edit-only contract.
     * <p>
     * For create functionality, use {@link CreateViewContract}.
     *
     * @return false (always)
     */
    @Override
    public final boolean isCreateMode() {
        return false;
    }

    /**
     * Resolve the entity ID from the URL path.
     * <p>
     * Subclasses must implement this to extract the ID from path parameters.
     *
     * @return The resolved ID
     */
    protected abstract String resolveId();

    /**
     * Load the entity to be edited.
     * <p>
     * Typically reads an ID from path parameters and loads from a service.
     *
     * @return The entity to edit, or null if not found
     */
    public abstract T item();

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
            .with(ContextKeys.EDIT_ENTITY, item())
            .with(ContextKeys.EDIT_SCHEMA, schema())
            .with(ContextKeys.EDIT_LIST_ROUTE, listRoute())
            .with(ContextKeys.EDIT_IS_CREATE_MODE, false);
    }

    /**
     * Delete the current entity.
     * <p>
     * Called when the user clicks the Delete button in the edit form.
     * <p>
     * Default implementation throws {@link UnsupportedOperationException}.
     * Override to implement deletion logic.
     *
     * @return true if deletion succeeded, false otherwise
     */
    public boolean delete() {
        throw new UnsupportedOperationException("Delete not implemented for " + getClass().getSimpleName());
    }

    // ========================================================================
    // Delete Event Handling
    // ========================================================================

    /**
     * Handle delete request event.
     * <p>
     * Calls {@link #delete()} and navigates on success.
     *
     * @param isModalMode Whether in modal mode
     */
    protected void handleDeleteRequested(boolean isModalMode) {
        boolean success = delete();
        if (success) {
            onDeleteSuccess(isModalMode);
        } else {
            onDeleteFailure();
        }
    }

    /**
     * Called when delete succeeds.
     * <p>
     * Default: In modal mode, publishes "modalDeleteSuccess". Otherwise, navigates to list.
     *
     * @param isModalMode Whether in modal mode
     */
    protected void onDeleteSuccess(boolean isModalMode) {
        if (isModalMode) {
            lookup.publish(MODAL_DELETE_SUCCESS);
        } else {
            lookup.publish(EventKeys.NAVIGATE, listRoute());
        }
    }

    /**
     * Called when delete fails.
     * <p>
     * Default: Does nothing (stays on page). Override to show error notification.
     */
    protected void onDeleteFailure() {
        // Default: stay on page
    }
}
