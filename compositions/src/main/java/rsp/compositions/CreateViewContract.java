package rsp.compositions;

import rsp.component.ComponentContext;
import rsp.component.Lookup;

/**
 * CreateViewContract - Base contract for creating new entities.
 * <p>
 * Provides a focused contract for entity creation without the complexity
 * of edit-mode detection or entity loading. Always in create mode.
 * <p>
 * Key differences from {@link EditViewContract}:
 * <ul>
 *   <li>No entity loading - form always starts empty</li>
 *   <li>No delete operation - cannot delete what doesn't exist yet</li>
 *   <li>No ID resolution - no existing entity to identify</li>
 *   <li>Simpler event handling - only form submission</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class PostCreateContract extends CreateViewContract<Post> {
 *     private final PostService postService;
 *
 *     public PostCreateContract(Lookup lookup) {
 *         super(lookup);
 *         this.postService = lookup.get(PostService.class);
 *     }
 *
 *     @Override
 *     public DataSchema schema() {
 *         return DataSchema.builder()
 *             .field("title", FieldType.STRING).required()
 *             .field("content", FieldType.TEXT)
 *             .build();
 *     }
 *
 *     @Override
 *     public boolean save(Map<String, Object> fieldValues) {
 *         Post post = new Post(null,
 *             (String) fieldValues.get("title"),
 *             (String) fieldValues.get("content"));
 *         postService.create(post);
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * @param <T> The type of entity being created
 */
public abstract class CreateViewContract<T> extends FormViewContract<T> {

    protected CreateViewContract(final Lookup lookup) {
        super(lookup);

        // In overlay mode, activate when OPEN_CREATE_MODAL is received
        if (isModalMode) {
            lookup.subscribe(EventKeys.OPEN_CREATE_MODAL, () -> {
                setActive();
            });

            // For auto-opened overlays (Case 2: OVERLAY + route), activate immediately
            Boolean isAutoOpen = lookup.get(ContextKeys.IS_AUTO_OPEN_OVERLAY);
            if (isAutoOpen != null && isAutoOpen) {
                setActive();
            }
        }
    }

    /**
     * Always returns true - this is a create-only contract.
     *
     * @return true (always)
     */
    @Override
    public final boolean isCreateMode() {
        return true;
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
            .with(ContextKeys.EDIT_ENTITY, null)
            .with(ContextKeys.EDIT_SCHEMA, schema())
            .with(ContextKeys.EDIT_LIST_ROUTE, listRoute())
            .with(ContextKeys.EDIT_IS_CREATE_MODE, true);
    }
}
