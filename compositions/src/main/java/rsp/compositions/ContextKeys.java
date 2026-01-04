package rsp.compositions;

import rsp.component.ContextKey;
import rsp.compositions.posts.services.PostService;
import rsp.server.http.HttpRequest;

import java.util.List;

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
     * Post service for CRUD operations on posts.
     * Stored as: PostService.class → PostService instance
     */
    public static final ContextKey.ClassKey<PostService> POST_SERVICE =
            new ContextKey.ClassKey<>(PostService.class);

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
     * Type: ListSchema
     * Defines column names, types, and rendering information.
     */
    public static final ContextKey.StringKey<ListSchema> LIST_SCHEMA =
            new ContextKey.StringKey<>("list.schema", ListSchema.class);

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
     * Type: ListSchema
     * Defines field names, types, and validation rules.
     */
    public static final ContextKey.StringKey<ListSchema> EDIT_SCHEMA =
            new ContextKey.StringKey<>("edit.schema", ListSchema.class);

    /**
     * Whether the current user is authenticated.
     * Type: Boolean
     * Example: true if user is logged in
     */
    public static final ContextKey.StringKey<Boolean> AUTH_AUTHENTICATED =
            new ContextKey.StringKey<>("auth.authenticated", Boolean.class);

    /**
     * List of application modules.
     * Type: {@code List<Module>}
     * Contains all registered service modules.
     */
    @SuppressWarnings("unchecked")
    public static final ContextKey.StringKey<List<Module>> APP_MODULES =
            new ContextKey.StringKey<>("app.modules",
                    (Class<List<Module>>) (Class<?>) List.class);

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
