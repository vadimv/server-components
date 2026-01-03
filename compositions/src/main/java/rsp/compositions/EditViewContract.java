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

    /**
     * Save the entity with the given field values.
     * <p>
     * Called when the user clicks the Save button in the edit form.
     *
     * @param fieldValues Map of field names to values from the form
     * @return true if save succeeded, false otherwise
     */
    public abstract boolean save(java.util.Map<String, Object> fieldValues);

    /**
     * Get the list route to navigate back to after save/cancel.
     * <p>
     * Default implementation uses convention based on the route pattern:
     * Removes the last segment if it's a parameter (starts with :).
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "/posts/:id" → "/posts"}</li>
     *   <li>{@code "/admin/users/:userId" → "/admin/users"}</li>
     *   <li>{@code "/posts/:postId/comments/:id" → "/posts/:postId/comments"}</li>
     * </ul>
     * <p>
     * Override this method if your navigation flow doesn't follow this convention
     * (e.g., nested parameterized routes, cross-module navigation, or query params).
     *
     * @return The list route path
     */
    public String listRoute() {
        String routePattern = (String) context.getAttribute("route.pattern");
        if (routePattern == null) {
            throw new IllegalStateException("route.pattern not found in context");
        }

        // Convention: remove last segment if it's a parameter
        // "/posts/:id" → "/posts"
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastSegment = routePattern.substring(lastSlash + 1);

            // If last segment is a parameter (starts with :), strip it
            if (lastSegment.startsWith(":")) {
                return routePattern.substring(0, lastSlash);
            }
        }

        // Fallback: if no param segment found, return root
        // This handles edge cases like "/edit" routes
        return "/";
    }
}
