package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.Lookup;

import static rsp.compositions.contract.EventKeys.DELETE_REQUESTED;
import static rsp.compositions.contract.EventKeys.MODAL_DELETE_SUCCESS;

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

        // Handle delete request - only if this is the active overlay
        lookup.subscribe(DELETE_REQUESTED, () -> {
            if (shouldHandleEvent()) {
                handleDeleteRequested();
            }
        });
    }

    @Override
    public void registerHandlers() {
        super.registerHandlers();

        // On-demand instantiation: detect SHOW_DATA (placement-agnostic)
        java.util.Map<String, Object> showData = lookup.get(ContextKeys.SHOW_DATA);
        if (showData != null && showData.get("id") != null) {
            String entityId = String.valueOf(showData.get("id"));
            setOverlayEntityId(entityId);
            setActive();
        }
    }

    /**
     * Set the entity ID for on-demand activation.
     * Used when contract is instantiated via SHOW event with data.
     *
     * @param id The entity ID
     */
    protected void setOverlayEntityId(String id) {
        this.overlayEntityId = id;
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
    // Delete Event Handling - Simplified, uses generic utilities
    // ========================================================================

    /**
     * Handle delete request event.
     * <p>
     * Calls {@link #delete()} and navigates on success.
     */
    protected void handleDeleteRequested() {
        boolean success = delete();
        if (success) {
            onDeleteSuccess();
        } else {
            onDeleteFailure();
        }
    }

    /**
     * Called when delete succeeds.
     * <p>
     * Same pattern as onSaveSuccess() - contracts decide based on slot.
     * <ul>
     *   <li>OVERLAY slot: emit HIDE to close overlay, emit REFRESH_LIST to update data</li>
     *   <li>PRIMARY slot: emit NAVIGATE to list route</li>
     * </ul>
     */
    protected void onDeleteSuccess() {
        Class<? extends ViewContract> contractClass = lookup.get(ContextKeys.CONTRACT_CLASS);
        Scene scene = lookup.get(ContextKeys.SCENE);

        // Use generic utility - no application-specific logic
        if (SlotUtils.isInOverlay(contractClass, scene)) {
            // Overlay: close and refresh
            lookup.publish(EventKeys.HIDE, contractClass);
            // Also emit legacy event for backward compatibility
            lookup.publish(MODAL_DELETE_SUCCESS);
            // Refresh list to show updated data
            lookup.publish(EventKeys.REFRESH_LIST);
        } else {
            // PRIMARY: navigate to list
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
