package rsp.compositions.contract;

import rsp.component.ContextKey;
import rsp.compositions.schema.DataSchema;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.module.Module;
import rsp.compositions.module.Slot;
import rsp.compositions.module.UiRegistry;
import rsp.compositions.routing.Router;
import rsp.server.Path;
import rsp.server.http.HttpRequest;

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
     * HTTP request object containing request details.
     * Stored as: HttpRequest.class → HttpRequest instance
     */
    public static final ContextKey.ClassKey<HttpRequest> HTTP_REQUEST =
            new ContextKey.ClassKey<>(HttpRequest.class);

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
     * View contract for the current route.
     * Stored as: ViewContract.class → ViewContract instance
     */
    public static final ContextKey.ClassKey<ViewContract> VIEW_CONTRACT =
            new ContextKey.ClassKey<>(ViewContract.class);

    /**
     * Authorization strategy for access control.
     * Stored as: ViewContract.AuthorizationStrategy.class → ViewContract.AuthorizationStrategy instance
     */
    public static final ContextKey.ClassKey<ViewContract.AuthorizationStrategy> AUTHORIZATION_STRATEGY =
            new ContextKey.ClassKey<>(ViewContract.AuthorizationStrategy.class);

    // ===== STRING-BASED KEYS (Namespaced metadata) =====

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
     * The slot type for the edit contract in this module.
     * Type: Slot
     * Values: Slot.PRIMARY or Slot.OVERLAY
     * Used by list view to determine how to handle edit button clicks.
     */
    public static final ContextKey.StringKey<Slot> EDIT_SLOT =
            new ContextKey.StringKey<>("edit.slot", Slot.class);

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
     * Map of all overlay contracts for this scene.
     * Type: {@code Map<Class<? extends ViewContract>, ViewContract>}
     * Contains pre-instantiated contracts for all Slot.OVERLAY placements.
     * Keyed by contract class for lookup.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Map<Class<? extends ViewContract>, ViewContract>> OVERLAY_CONTRACTS =
            new ContextKey.StringKey<>("layout.overlayContracts",
                    (Class<Map<Class<? extends ViewContract>, ViewContract>>) (Class<?>) Map.class);

    /**
     * Base key for overlay view contract instances.
     * Use {@code OVERLAY_VIEW_CONTRACT.with(contractClassName)} to access specific overlay contracts.
     * Type: ViewContract
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code OVERLAY_VIEW_CONTRACT.with("PostCreateContract")} - create overlay contract</li>
     *   <li>{@code OVERLAY_VIEW_CONTRACT.with("PostEditContract")} - edit overlay contract</li>
     * </ul>
     */
    public static final ContextKey.DynamicKey<ViewContract> OVERLAY_VIEW_CONTRACT =
            new ContextKey.DynamicKey<>("layout.overlayViewContract", ViewContract.class);

    /**
     * Whether the current contract is being instantiated as an overlay (modal/popup).
     * Type: Boolean
     * Set to true when SceneComponent instantiates contracts with Slot.OVERLAY.
     * Contracts can check this to adjust their behavior (e.g., publish modal events instead of navigating).
     */
    public static final ContextKey.StringKey<Boolean> IS_OVERLAY_MODE =
            new ContextKey.StringKey<>("layout.isOverlayMode", Boolean.class);

    /**
     * Whether the current overlay contract is being auto-opened (Case 2: OVERLAY + route).
     * Type: Boolean
     * Set to true when SceneComponent instantiates an overlay contract that was routed directly via URL.
     * Contracts should immediately activate themselves when this is true.
     */
    public static final ContextKey.StringKey<Boolean> IS_AUTO_OPEN_OVERLAY =
            new ContextKey.StringKey<>("layout.isAutoOpenOverlay", Boolean.class);

    /**
     * The currently active overlay contract class.
     * Type: Class (contract class)
     * Set by LayoutComponent when an overlay is activated.
     * Used by overlay contracts to determine if they should handle events.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<Class<? extends ViewContract>> ACTIVE_OVERLAY_CLASS =
            new ContextKey.StringKey<>("layout.activeOverlayClass",
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
     * List of application modules.
     * Type: {@code List<Module>}
     * Contains all registered service modules.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<rsp.compositions.module.Module>> APP_MODULES =
            new ContextKey.StringKey<>("app.modules",
                    (Class<List<Module>>) (Class<?>) List.class);

    /**
     * Default page size for list views.
     * Type: Integer
     * This is a framework-agnostic configuration value that list view contracts
     * can access without depending on AppConfig structure.
     * Example: 10 (show 10 items per page)
     */
    public static final ContextKey.StringKey<Integer> LIST_DEFAULT_PAGE_SIZE =
            new ContextKey.StringKey<>("list.defaultPageSize", Integer.class);

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
