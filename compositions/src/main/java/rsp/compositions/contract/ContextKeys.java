package rsp.compositions.contract;

import rsp.component.ContextKey;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
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
     * UI registry for mapping view contracts to UI implementations.
     * Stored as: UiRegistry.class → UiRegistry instance
     */
    public static final ContextKey.ClassKey<UiRegistry> UI_REGISTRY =
            new ContextKey.ClassKey<>(UiRegistry.class);


    /**
     * Authentication provider for user authentication.
     * Stored as: AuthComponent.AuthProvider.class → AuthComponent.AuthProvider instance
     */
    public static final ContextKey.ClassKey<AuthComponent.AuthProvider> AUTH_PROVIDER =
            new ContextKey.ClassKey<>(AuthComponent.AuthProvider.class);

    /**
     * Authorization strategy for access control.
     * Stored as: ViewContract.AuthorizationStrategy.class → ViewContract.AuthorizationStrategy instance
     */
    public static final ContextKey.ClassKey<ViewContract.AuthorizationStrategy> AUTHORIZATION_STRATEGY =
            new ContextKey.ClassKey<>(ViewContract.AuthorizationStrategy.class);

    // ===== STRING-BASED KEYS (Namespaced metadata) =====

    /**
     * The composition that matched the current route.
     * Type: Composition
     * Populated by RoutingComponent when iterating Compositions for route matching.
     */
    public static final ContextKey.ClassKey<Composition> ROUTE_COMPOSITION =
            new ContextKey.ClassKey<>(Composition.class);

    /**
     * The contract class for the current route.
     * Type: {@code Class<? extends ViewContract>}
     * Example: PostsListContract.class
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Class<? extends ViewContract>> ROUTE_CONTRACT_CLASS =
            new ContextKey.StringKey<>("route.contractClass",
                    (Class<Class<? extends ViewContract>>) (Class<?>) Class.class);

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
     * List of items to display in ListView.
     * Type: {@code List<?>} (typically {@code List<Map<String, Object>>})
     * Each map represents a database row with column names as keys.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<?>> LIST_ITEMS =
            new ContextKey.StringKey<>("list.items",
                    (Class<List<?>>) (Class<?>) List.class);

    /**
     * Schema definition for list columns.
     * Type: DataSchema
     * Defines column names, types, and rendering information.
     */
    public static final ContextKey.StringKey<DataSchema> LIST_SCHEMA =
            new ContextKey.StringKey<>("list.schema", DataSchema.class);

    /**
     * Current page number for paginated lists.
     * Type: Integer
     * Example: 3 (for page 3)
     */
    public static final ContextKey.StringKey<Integer> LIST_PAGE =
            new ContextKey.StringKey<>("list.page", Integer.class);

    /**
     * Sort direction for list view.
     * Type: String
     * Values: "asc" or "desc"
     */
    public static final ContextKey.StringKey<String> LIST_SORT =
            new ContextKey.StringKey<>("list.sort", String.class);

    /**
     * Entity being edited in EditView.
     * Type: Object (typically {@code Map<String, Object>})
     * Represents a single database row.
     */
    public static final ContextKey.StringKey<Object> EDIT_ENTITY =
            new ContextKey.StringKey<>("edit.entity", Object.class);

    /**
     * Schema definition for edit form fields.
     * Type: DataSchema
     * Defines field names, types, and validation rules.
     */
    public static final ContextKey.StringKey<DataSchema> EDIT_SCHEMA =
            new ContextKey.StringKey<>("edit.schema", DataSchema.class);

    /**
     * Whether the current edit view is in create mode.
     * Type: Boolean
     * True when creating a new entity, false when editing existing.
     */
    public static final ContextKey.StringKey<Boolean> EDIT_IS_CREATE_MODE =
            new ContextKey.StringKey<>("edit.isCreateMode", Boolean.class);

    /**
     * The route to navigate back to list view.
     * Type: String
     * Example: {@literal /posts} or {@literal /posts?p=3&sort=desc}
     * Used by EditView to know where to navigate after save/cancel.
     */
    public static final ContextKey.StringKey<String> EDIT_LIST_ROUTE =
            new ContextKey.StringKey<>("edit.listRoute", String.class);

    /**
     * Whether the edit contract has a registered route.
     * Type: Boolean
     * True if Router has a route for the edit contract (e.g., "/posts/:id").
     * Used by list view to determine edit button behavior (URL navigation vs event-only).
     */
    public static final ContextKey.StringKey<Boolean> EDIT_HAS_ROUTE =
            new ContextKey.StringKey<>("edit.hasRoute", Boolean.class);

    /**
     * The route pattern for the edit contract (if it has one).
     * Type: String
     * Example: "/posts/:id"
     * Used by list view to build edit URLs when EDIT_HAS_ROUTE is true.
     */
    public static final ContextKey.StringKey<String> EDIT_ROUTE_PATTERN =
            new ContextKey.StringKey<>("edit.routePattern", String.class);

    /**
     * Whether the edit contract opens as an overlay (has a parent route).
     * Type: Boolean
     * <p>
     * True when the edit contract's route has a parent route (e.g., "/posts/:id" has parent "/posts"),
     * meaning it opens as an overlay via SHOW event rather than navigating as a primary view.
     * Used by list view to determine whether the edit button renders as a link or a SHOW button.
     */
    public static final ContextKey.StringKey<Boolean> EDIT_OPENS_AS_OVERLAY =
            new ContextKey.StringKey<>("edit.opensAsOverlay", Boolean.class);

    /**
     * Data passed to a contract when shown via SHOW event.
     * Type: {@code Map<String, Object>}
     * Example: {id: "123"} for edit contract
     * <p>
     * Set by SceneComponent when instantiating a contract on SHOW event.
     * Contracts read this in their constructor or registerHandlers() to get
     * entity IDs or other data needed for initialization.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Map<String, Object>> SHOW_DATA =
            new ContextKey.StringKey<>("show.data",
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

    /**
     * Whether this contract instance is currently active.
     * Type: Boolean
     * <p>
     * Set to true by SceneComponent when instantiating a contract.
     * Used by contracts to determine if they should handle events.
     * When multiple overlays are stacked, only the topmost has IS_ACTIVE_CONTRACT=true.
     * <p>
     * Replaces local isActiveOverlay field - contracts read from context instead of storing state.
     */
    public static final ContextKey.StringKey<Boolean> IS_ACTIVE_CONTRACT =
            new ContextKey.StringKey<>("contract.isActive", Boolean.class);

    /**
     * The current scene.
     * Type: Scene
     * <p>
     * Set by SceneComponent in subComponentsContext.
     * Available to downstream components for contract/factory lookups.
     */
    public static final ContextKey.StringKey<Scene> SCENE =
            new ContextKey.StringKey<>("scene", Scene.class);

    /**
     * The contract class that is currently being hidden.
     * Type: {@code Class<? extends ViewContract>}
     * <p>
     * Set temporarily when HIDE event is processed.
     * Used by DefaultEditView to know its own contract class for HIDE events.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Class<? extends ViewContract>> CONTRACT_CLASS =
            new ContextKey.StringKey<>("contract.class",
                    (Class<Class<? extends ViewContract>>) (Class<?>) Class.class);

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
     * The title of the current contract.
     * Used by views to derive context-specific titles.
     */
    public static final ContextKey.StringKey<String> CONTRACT_TITLE =
            new ContextKey.StringKey<>("contract.title", String.class);

    /**
     * The title of the currently active overlay contract.
     * Used by EditView/CreateView to display their title independently of the primary contract's title.
     * Type: String (e.g., "Edit Post", "Create Comment")
     */
    public static final ContextKey.StringKey<String> OVERLAY_TITLE =
            new ContextKey.StringKey<>("overlay.title", String.class);

    /**
     * The category key of the current primary contract.
     * Used by Explorer to highlight the active menu item.
     * Type: String (e.g., "Posts", "Comments")
     *
     * Set by SceneContextEnricher and updated when the primary contract changes via SET_PRIMARY.
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
     * Pre-computed navigation entries for PRIMARY contracts.
     * Type: {@code List<NavigationEntry>}
     * <p>
     * Computed from composition registrations and explicit categories at app startup.
     * Used by navigation/explorer components to render menus without instantiating contracts.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<NavigationEntry>> NAVIGATION_ENTRIES =
            new ContextKey.StringKey<>("app.navigationEntries",
                    (Class<List<NavigationEntry>>) (Class<?>) List.class);

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
