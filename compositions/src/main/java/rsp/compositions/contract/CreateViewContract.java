package rsp.compositions.contract;

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
        // No event subscriptions - placement-agnostic
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context
            .with(ContextKeys.CONTRACT_CLASS, getClass())
            .with(ContextKeys.EDIT_ENTITY, null)
            .with(ContextKeys.EDIT_SCHEMA, schema())
            .with(ContextKeys.EDIT_LIST_ROUTE, listRoute())
            .with(ContextKeys.EDIT_IS_CREATE_MODE, true)
            .with(ContextKeys.CONTRACT_TITLE, title());
    }
}
