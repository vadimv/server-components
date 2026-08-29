package rsp.compositions.block;

/**
 * Navigation metadata for a routable block.
 * <p>
 * Each entry represents a unique navigable category with its
 * route and display label. Used by navigation/explorer UI components
 * to render menus.
 *
 * @param categoryKey the category key used for active highlighting
 * @param label     display label for navigation
 * @param blockKey configured block key used for SET_PRIMARY events
 * @param blockClass concrete block class used for metadata
 * @param route     the route pattern (e.g., "/posts")
 */
public record NavigationEntry(String categoryKey,
                              String label,
                              Object blockKey,
                              Class<? extends Block<?, ?>> blockClass,
                              String route) {
    public NavigationEntry(String categoryKey,
                           String label,
                           Class<? extends Block<?, ?>> blockClass,
                           String route) {
        this(categoryKey, label, blockClass, blockClass, route);
    }
}
