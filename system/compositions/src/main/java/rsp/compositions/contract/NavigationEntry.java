package rsp.compositions.contract;

/**
 * Navigation metadata for a routable contract.
 * <p>
 * Each entry represents a unique navigable category with its
 * route and display label. Used by navigation/explorer UI components
 * to render menus.
 *
 * @param categoryKey the category key used for active highlighting
 * @param label     display label for navigation
 * @param contractClass the contract class (for SET_PRIMARY events)
 * @param route     the route pattern (e.g., "/posts")
 */
public record NavigationEntry(String categoryKey,
                              String label,
                              Class<? extends ViewContract> contractClass,
                              String route) {
}
