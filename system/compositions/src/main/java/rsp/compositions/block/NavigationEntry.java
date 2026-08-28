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
 * @param blockClass the block class (for SET_PRIMARY events)
 * @param route     the route pattern (e.g., "/posts")
 */
public record NavigationEntry(String categoryKey,
                              String label,
                              Class<? extends Block<?, ?>> blockClass,
                              String route) {
}
