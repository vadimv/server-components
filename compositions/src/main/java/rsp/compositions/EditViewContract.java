package rsp.compositions;

import rsp.component.ComponentContext;

/**
 * EditViewContract - Base contract for edit/form views.
 * <p>
 * Provides the entity being edited and schema metadata for adaptive rendering.
 * Concrete implementations load entities by ID extracted from URL path parameters.
 *
 * @param <T> The type of entity being edited
 */
public abstract class EditViewContract<T> extends ViewContract {

    protected EditViewContract(ComponentContext context) {
        super(context);
    }

    /**
     * Load the entity to be edited.
     * <p>
     * Typically reads an ID from path parameters and loads from a service.
     *
     * @return The entity to edit, or null if not found
     */
    public abstract T item();

    /**
     * Get the schema for the entity's fields.
     * <p>
     * Can be auto-derived from the entity if not explicitly provided.
     *
     * @return Schema metadata for rendering form fields
     */
    public abstract ListSchema schema();
}
