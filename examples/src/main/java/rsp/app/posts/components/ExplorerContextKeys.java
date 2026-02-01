package rsp.app.posts.components;

import rsp.component.ContextKey;

import java.util.List;

/**
 * Context keys for Explorer component data.
 * These are app-level keys, not framework-level.
 */
public final class ExplorerContextKeys {
    private ExplorerContextKeys() {} // Prevent instantiation

    /**
     * List of explorer menu items.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<ExplorerItem>> EXPLORER_ITEMS =
            new ContextKey.StringKey<>("explorer.items", (Class<List<ExplorerItem>>) (Class<?>) List.class);

    /**
     * The currently active typeHint (for highlighting in menu).
     */
    public static final ContextKey.StringKey<Object> EXPLORER_ACTIVE_HINT =
            new ContextKey.StringKey<>("explorer.activeHint", Object.class);
}
