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
     * Also restores query parameters that were preserved from the list view
     * (e.g., fromP → p, fromSort → sort).
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code "/posts/:id" → "/posts"}</li>
     *   <li>{@code "/posts/:id" with fromP=3 → "/posts?p=3"}</li>
     *   <li>{@code "/posts/:id" with fromP=3&fromSort=desc → "/posts?p=3&sort=desc"}</li>
     *   <li>{@code "/admin/users/:userId" → "/admin/users"}</li>
     *   <li>{@code "/posts/:postId/comments/:id" → "/posts/:postId/comments"}</li>
     * </ul>
     * <p>
     * Override this method if your navigation flow doesn't follow this convention
     * (e.g., nested parameterized routes, cross-module navigation, or custom query params).
     *
     * @return The list route path with restored query parameters
     */
    public String listRoute() {
        String routePattern = (String) context.getAttribute("route.pattern");
        if (routePattern == null) {
            throw new IllegalStateException("route.pattern not found in context");
        }

        // Convention: remove last segment if it's a parameter
        // "/posts/:id" → "/posts"
        String basePath;
        int lastSlash = routePattern.lastIndexOf('/');
        if (lastSlash > 0) {
            String lastSegment = routePattern.substring(lastSlash + 1);

            // If last segment is a parameter (starts with :), strip it
            if (lastSegment.startsWith(":")) {
                basePath = routePattern.substring(0, lastSlash);
            } else {
                basePath = "/";
            }
        } else {
            basePath = "/";
        }

        // Restore query parameters from url.query.from* attributes
        String queryString = buildRestoredQueryString();
        if (queryString.isEmpty()) {
            return basePath;
        }

        return basePath + "?" + queryString;
    }

    /**
     * Build query string by restoring from* parameters to original names.
     * <p>
     * Reads url.query.fromP, url.query.fromSort, etc. from context
     * and converts them back to original names (fromP → p, fromSort → sort).
     *
     * @return Query string (e.g., "p=3&sort=desc"), or empty string
     */
    private String buildRestoredQueryString() {
        java.util.List<String> params = new java.util.ArrayList<>();

        // Restore page parameter (fromP → p)
        String fromP = (String) context.getAttribute("url.query.fromP");
        if (fromP != null && !fromP.isEmpty()) {
            params.add("p=" + fromP);
        }

        // Restore sort parameter (fromSort → sort)
        String fromSort = (String) context.getAttribute("url.query.fromSort");
        if (fromSort != null && !fromSort.isEmpty()) {
            params.add("sort=" + fromSort);
        }

        return params.isEmpty() ? "" : String.join("&", params);
    }
}
