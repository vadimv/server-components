package rsp.compositions.block;

import rsp.component.ContextKey;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.routing.Router;
import rsp.server.Path;

import java.util.List;
import java.util.Map;

/**
 * Registry of all type-safe context keys used in the compositions module.
 *
 * <p>This class provides a centralized location for all context attribute keys,
 * enabling IDE autocomplete, type safety, and refactoring support.</p>
 *
 * <p><strong>Key Categories:</strong></p>
 * <ul>
 *   <li><strong>ClassKey</strong> - Services and components (ServiceLoader pattern)</li>
 *   <li><strong>StringKey</strong> - Namespaced metadata attributes</li>
 *   <li><strong>DynamicKey</strong> - Parameterized keys (url.query.*, url.path.*)</li>
 * </ul>
 */
public final class ContextKeys {
    private ContextKeys() {} // Prevent instantiation

    // ===== CLASS-BASED KEYS (ServiceLoader style) =====

    /**
     * Router service for URL routing and path matching.
     * Stored as: Router.class → Router instance
     */
    public static final ContextKey.ClassKey<Router> ROUTER =
            new ContextKey.ClassKey<>(Router.class);

    /**
     * Authentication provider for user authentication.
     * Stored as: AuthComponent.AuthProvider.class → AuthComponent.AuthProvider instance
     */
    public static final ContextKey.ClassKey<AuthComponent.AuthProvider> AUTH_PROVIDER =
            new ContextKey.ClassKey<>(AuthComponent.AuthProvider.class);

    /**
     * Authorization strategy for access control.
     * Stored as: BlockRuntime.AuthorizationStrategy.class → BlockRuntime.AuthorizationStrategy instance
     */
    public static final ContextKey.ClassKey<BlockRuntime.AuthorizationStrategy> AUTHORIZATION_STRATEGY =
            new ContextKey.ClassKey<>(BlockRuntime.AuthorizationStrategy.class);

    // ===== STRING-BASED KEYS (Namespaced metadata) =====

    /**
     * The composition that matched the current route.
     * Type: Composition
     * Populated by RoutingComponent when iterating Compositions for route matching.
     */
    public static final ContextKey.ClassKey<Composition> ROUTE_COMPOSITION =
            new ContextKey.ClassKey<>(Composition.class);

    /**
     * The block class for the current route.
     * Type: {@code Class<? extends Block<?, ?>>}
     * Example: PostsListBlock.class
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Class<? extends Block<?, ?>>> ROUTE_BLOCK_CLASS =
            new ContextKey.StringKey<>("route.blockClass",
                    (Class<Class<? extends Block<?, ?>>>) (Class<?>) Class.class);

    /** Configured binding key for the current route. */
    public static final ContextKey.StringKey<Object> ROUTE_BLOCK_KEY =
            new ContextKey.StringKey<>("route.blockKey", Object.class);

    /**
     * The URL path matched by the router.
     * Type: String
     * Example: "/posts/123"
     */
    public static final ContextKey.StringKey<String> ROUTE_PATH =
            new ContextKey.StringKey<>("route.path", String.class);

    /**
     * The route pattern with placeholders.
     * Type: String
     * Example: "/posts/:id"
     */
    public static final ContextKey.StringKey<String> ROUTE_PATTERN =
            new ContextKey.StringKey<>("route.pattern", String.class);

    /**
     * Whether the edit block has a registered route.
     * Type: Boolean
     * True if Router has a route for the edit block (e.g., "/posts/:id").
     * Used by list view to determine edit button behavior (URL navigation vs event-only).
     */
    public static final ContextKey.StringKey<Boolean> EDIT_HAS_ROUTE =
            new ContextKey.StringKey<>("edit.hasRoute", Boolean.class);

    /**
     * The route pattern for the edit block (if it has one).
     * Type: String
     * Example: "/posts/:id"
     * Used by list view to build edit URLs when EDIT_HAS_ROUTE is true.
     */
    public static final ContextKey.StringKey<String> EDIT_ROUTE_PATTERN =
            new ContextKey.StringKey<>("edit.routePattern", String.class);

    /**
     * Whether the edit block opens as an overlay (has a parent route).
     * Type: Boolean
     * <p>
     * True when the edit block's route has a parent route (e.g., "/posts/:id" has parent "/posts"),
     * meaning it opens as an overlay via SHOW event rather than navigating as a primary view.
     * Used by list view to determine whether the edit button renders as a link or a SHOW button.
     */
    public static final ContextKey.StringKey<Boolean> EDIT_OPENS_AS_OVERLAY =
            new ContextKey.StringKey<>("edit.opensAsOverlay", Boolean.class);

    /**
     * Data passed to a block when shown via SHOW event.
     * Type: {@code Map<String, Object>}
     * Example: {id: "123"} for edit block
     * <p>
     * Set by DirectBlockHost when it mounts a descriptor selected by SHOW.
     * Blocks read this during state initialization to get
     * entity IDs or other data needed for initialization.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Map<String, Object>> SHOW_DATA =
            new ContextKey.StringKey<>("show.data",
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

    /**
     * Whether this block instance is currently active.
     * Type: Boolean
     * <p>
     * Set to true by DirectBlockHost while it owns the block component.
     * Used by blocks to determine if they should handle events.
     * When multiple overlays are stacked, only the topmost has IS_ACTIVE_BLOCK=true.
     * <p>
     * Replaces local isActiveOverlay field - blocks read from context instead of storing state.
     */
    public static final ContextKey.StringKey<Boolean> IS_ACTIVE_BLOCK =
            new ContextKey.StringKey<>("block.isActive", Boolean.class);

    /**
     * The current scene.
     * Type: Scene
     * <p>
     * Set by SceneComponent in subComponentsContext.
     * Available to downstream components for block/factory lookups.
     */
    public static final ContextKey.StringKey<Scene> SCENE =
            new ContextKey.StringKey<>("scene", Scene.class);

    /**
     * The block class that is currently being hidden.
     * Type: {@code Class<? extends Block<?, ?>>}
     * <p>
     * Set temporarily when HIDE event is processed.
     * Used by DefaultEditView to know its own block class for HIDE events.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Class<? extends Block<?, ?>>> BLOCK_CLASS =
            new ContextKey.StringKey<>("block.class",
                    (Class<Class<? extends Block<?, ?>>>) (Class<?>) Class.class);

    /** Configured binding key for the active block instance. */
    public static final ContextKey.StringKey<Object> BLOCK_KEY =
            new ContextKey.StringKey<>("block.key", Object.class);

    /**
     * The authenticated user object.
     * Type: Object (application-specific user type)
     * Example: User instance with id, username, etc.
     */
    public static final ContextKey.StringKey<Object> AUTH_USER =
            new ContextKey.StringKey<>("auth.user", Object.class);

    /**
     * Whether the current user is authenticated.
     * Type: Boolean
     * Example: true if user is logged in
     */
    public static final ContextKey.StringKey<Boolean> AUTH_AUTHENTICATED =
            new ContextKey.StringKey<>("auth.authenticated", Boolean.class);

    /**
     * The roles assigned to the current user.
     * Type: String[] (array of role names)
     * Example: ["admin", "user"]
     */
    public static final ContextKey.StringKey<String[]> AUTH_ROLES =
            new ContextKey.StringKey<>("auth.roles", String[].class);


    /**
     * The title of the current block.
     * Used by views to derive context-specific titles.
     */
    public static final ContextKey.StringKey<String> BLOCK_TITLE =
            new ContextKey.StringKey<>("block.title", String.class);

    /**
     * The title of the currently active overlay block.
     * Used by edit block views to display their title independently of the primary block's title.
     * Type: String (e.g., "Edit Post", "Create Comment")
     */
    public static final ContextKey.StringKey<String> OVERLAY_TITLE =
            new ContextKey.StringKey<>("overlay.title", String.class);

    /**
     * The category key of the current primary block.
     * Used by Explorer to highlight the active menu item.
     * Type: String (e.g., "Posts", "Comments")
     *
     * Set by SceneContextEnricher and updated when the primary block changes via SET_PRIMARY.
     */
    public static final ContextKey.StringKey<String> PRIMARY_CATEGORY_KEY =
            new ContextKey.StringKey<>("primary.categoryKey", String.class);

    /**
     * List of application compositions.
     * Type: {@code List<Composition>}
     * Contains all registered feature compositions.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<rsp.compositions.composition.Composition>> APP_COMPOSITIONS =
            new ContextKey.StringKey<>("app.compositions",
                    (Class<List<Composition>>) (Class<?>) List.class);

    /**
     * Pre-computed navigation tree for PRIMARY blocks.
     * Type: {@link NavigationNode}
     * <p>
     * Built from the application's group structure and routers. The root node is
     * anonymous (no label/entry) and its children carry the labelled groups and
     * routable leaves.
     */
    public static final ContextKey.StringKey<NavigationNode> NAVIGATION_TREE =
            new ContextKey.StringKey<>("app.navigationTree", NavigationNode.class);

    // ===== URL KEYS =====

    /**
     * Full URL path as a Path object.
     * Populated by AutoAddressBarSyncComponent.
     * Type: Path
     *
     * <p>Used by RoutingComponent to match routes without depending on HttpRequest.</p>
     */
    public static final ContextKey.StringKey<Path> URL_PATH_FULL =
            new ContextKey.StringKey<>("url.path", Path.class);

    /**
     * Current URL fragment (the part after {@code #}).
     * Populated by AutoAddressBarSyncComponent when the URL has a non-empty fragment.
     * Type: String
     */
    public static final ContextKey.StringKey<String> URL_FRAGMENT =
            new ContextKey.StringKey<>("url.fragment", String.class);

    // ===== DYNAMIC KEYS (Builder pattern for parameterized keys) =====

    /**
     * Base key for URL query parameters.
     * Use {@code URL_QUERY.with("paramName")} to access specific query params.
     * Type: String
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code URL_QUERY.with("p")} - page number from ?p=3</li>
     *   <li>{@code URL_QUERY.with("sort")} - sort direction from ?sort=asc</li>
     *   <li>{@code URL_QUERY.with("fromP")} - return page from ?fromP=2</li>
     * </ul>
     */
    public static final ContextKey.DynamicKey<String> URL_QUERY =
            new ContextKey.DynamicKey<>("url.query", String.class);

    /**
     * Base key for URL path parameters.
     * Use {@code URL_PATH.with("paramName")} to access specific path params.
     * Type: String
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code URL_PATH.with("id")} - ID from /posts/:id</li>
     *   <li>{@code URL_PATH.with("slug")} - slug from /articles/:slug</li>
     * </ul>
     */
    public static final ContextKey.DynamicKey<String> URL_PATH =
            new ContextKey.DynamicKey<>("url.path", String.class);

}
