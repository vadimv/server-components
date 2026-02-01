package rsp.app.posts.components;

/**
 * Represents an item in the Explorer menu.
 * Each item corresponds to a unique typeHint from registered contracts.
 *
 * @param typeHint The typeHint object (e.g., Post.class) used for identity/grouping
 * @param label The display label for the menu item
 * @param route The route to navigate to when clicked
 */
public record ExplorerItem(Object typeHint, String label, String route) {

    /**
     * Derive a display label from a typeHint.
     * <ul>
     *   <li>Class objects: simpleName + "s" (e.g., Post.class → "Posts")</li>
     *   <li>Other objects: toString()</li>
     * </ul>
     *
     * @param typeHint The typeHint object
     * @return A human-readable label
     */
    public static String deriveLabel(Object typeHint) {
        if (typeHint instanceof Class<?> clazz) {
            return clazz.getSimpleName() + "s";
        }
        return typeHint.toString();
    }
}
