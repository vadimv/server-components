package rsp.compositions.contract;

import rsp.component.ComponentContext;
import rsp.component.EventKey;
import rsp.component.Lookup;

/**
 * EditViewContract - Base contract for editing existing entities.
 * <p>
 * Provides functionality for loading and modifying existing entities:
 * <ul>
 *   <li>Entity loading via {@link #item()}</li>
 *   <li>Entity deletion via {@link #delete()}</li>
 *   <li>ID resolution from URL path or SHOW_DATA</li>
 * </ul>
 * <p>
 * For creating new entities, use {@link CreateViewContract} instead.
 * <p>
 * <b>Placement-agnostic:</b> This contract works in any slot (PRIMARY, OVERLAY, etc.)
 * without knowing about its placement. ID resolution automatically checks SHOW_DATA first
 * (for on-demand instantiation), then falls back to URL path parameters.
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
 *     protected String resolveIdFromPath() {
 *         return resolve(POST_ID);  // Extract ID from URL path parameter
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
     * Delete action requested (after confirmation).
     * Emitted by: DefaultEditView
     * Handled by: EditViewContract.registerHandlers()
     */
    public static final EventKey.VoidKey DELETE_REQUESTED =
            new EventKey.VoidKey("delete.requested");

    protected EditViewContract(final Lookup lookup) {
        super(lookup);

        // Handle delete request - only if this is the active contract
        subscribe(DELETE_REQUESTED, () -> {
            if (shouldHandleEvent()) {
                handleDeleteRequested();
            }
        });
    }

    /**
     * Resolve the entity ID.
     * <p>
     * <b>Placement-agnostic implementation:</b>
     * Checks SHOW_DATA first (on-demand instantiation), then falls back to URL path.
     * This allows the contract to work in any slot without knowing about its placement.
     *
     * @return The resolved ID
     */
    protected String resolveId() {
        // First check SHOW_DATA (placement-agnostic on-demand instantiation)
        java.util.Map<String, Object> showData = lookup.get(ContextKeys.SHOW_DATA);
        if (showData != null && showData.get("id") != null) {
            return String.valueOf(showData.get("id"));
        }

        // Otherwise resolve from URL path parameter
        return resolveIdFromPath();
    }

    /**
     * Resolve the entity ID from the URL path.
     * <p>
     * Subclasses must implement this to extract the ID from path parameters.
     * The parent {@link #resolveId()} method calls this as a fallback when SHOW_DATA is not available.
     *
     * @return The resolved ID from path
     */
    protected abstract String resolveIdFromPath();

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
            .with(ContextKeys.CONTRACT_CLASS, getClass())
            .with(ContextKeys.EDIT_ENTITY, item())
            .with(ContextKeys.EDIT_SCHEMA, schema())
            .with(ContextKeys.EDIT_LIST_ROUTE, listRoute())
            .with(ContextKeys.EDIT_IS_CREATE_MODE, false)
            .with(ContextKeys.CONTRACT_TITLE, title());
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
     * Emits ACTION_SUCCESS event - framework derives navigation from composition config.
     * This follows the CountersMainComponent pattern:
     * <ul>
     *   <li>Contract emits INTENT (action type only, no routes)</li>
     *   <li>Framework derives NAVIGATION from composition/router configuration</li>
     * </ul>
     */
    protected void onDeleteSuccess() {
        // Emit generic success event - framework derives navigation from composition
        lookup.publish(EventKeys.ACTION_SUCCESS,
            new EventKeys.ActionResult(getClass(), EventKeys.ActionType.DELETE));
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
