package rsp.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class is used to pass information from upstream components in a tree to their downstream components.
 * Values are added during an initial state rendering by a component and stored with a key after its state is resolved.
 * This way every component down the hierarchy can add new key-values overriding existing with the same key.
 *
 * <p>This class supports type-safe attribute access using {@link ContextKey} sealed interface variants:</p>
 * <ul>
 *   <li>{@link ContextKey.ClassKey} - for services and components (ServiceLoader pattern)</li>
 *   <li>{@link ContextKey.StringKey} - for namespaced metadata attributes</li>
 *   <li>{@link ContextKey.DynamicKey} - for parameterized keys like url.query.* and url.path.*</li>
 * </ul>
 *
 * <p><strong>Storage Strategy:</strong> Uses two separate internal maps to prevent collisions:</p>
 * <ul>
 *   <li>{@code Map<Class<?>, Object>} for ClassKey storage</li>
 *   <li>{@code Map<String, Object>} for StringKey and DynamicKey storage</li>
 * </ul>
 *
 * <p>By default, the context is propagated down the component tree. However, a component can choose to
 * isolate its subtree by creating a new, empty {@code ComponentContext} instead of extending the parent's context.</p>
 *
 * <p><strong>Security Policy:</strong></p>
 * <ul>
 *   <li><strong>ALLOWED:</strong> Service instances, request data, user sessions, business data, non-sensitive config (AppConfig)</li>
 *   <li><strong>FORBIDDEN:</strong> DB credentials, API secrets, encryption keys, private certificates</li>
 *   <li><strong>Critical Rule:</strong> Everything sensitive must be provided on Service init outside the Context.
 *       Services are initialized in main() with their configuration (which may contain secrets).
 *       Only service instances (not their config) are added to ComponentContext.</li>
 * </ul>
 *
 * <p><strong>Configuration Pattern:</strong></p>
 * <ul>
 *   <li><strong>AppConfig:</strong> Contains non-sensitive configuration (page size, locale, date format, etc.).
 *       Added to ComponentContext and accessible to all contracts/components.</li>
 *   <li><strong>Service Configuration:</strong> MAY contain secrets (DB credentials, API keys, etc.).
 *       Used ONLY during service initialization in application main(). NEVER added to ComponentContext.</li>
 *   <li>Service instances (not their config) are added to context for use by contracts</li>
 * </ul>
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * // In CrudApp.main()
 * AppConfig appConfig = AppConfig.defaults();  // Only non-sensitive config
 *
 * // Service gets secrets during init (secrets stay encapsulated in service)
 * PostService postService = new PostService(databaseConfig);  // databaseConfig has credentials
 *
 * // Only AppConfig and service INSTANCE go to context (NOT databaseConfig)
 * App app = new App(appConfig, ...);  // AppConfig flows to context
 * // Service instance added to context by AppComponent
 * }</pre>
 *
 * <p><strong>Security Notice:</strong> This context may contain sensitive information, such as session identifiers
 * or device IDs. By design, this information is accessible to all components in the subtree.
 * It is assumed that all components running on the server are trusted. Developers must ensure that
 * sensitive context values are not accidentally rendered into the client-side HTML or leaked to untrusted parties.</p>
 */
public final class ComponentContext {

    public static final String DEVICE_ID_KEY = "deviceId";
    public static final String SESSION_ID_KEY = "sessionId";

    // Separate maps for different key types to prevent collisions
    private final Map<Class<?>, Object> classBased;     // ClassKey storage
    private final Map<String, Object> stringBased;      // StringKey + DynamicKey storage

    /**
     * Creates a new, empty component context.
     * This constructor is typically used to create a root context or to isolate a subtree
     * from the parent context, effectively clearing all upstream attributes.
     */
    public ComponentContext() {
        this(new HashMap<>(), new HashMap<>());
    }

    private ComponentContext(final Map<Class<?>, Object> classBased,
                            final Map<String, Object> stringBased) {
        this.classBased = classBased;
        this.stringBased = stringBased;
    }

    // ===== TYPE-SAFE KEY-BASED METHODS (New API) =====

    /**
     * Retrieves a value by a type-safe key using pattern matching.
     * Returns null if the key is not present in the context.
     *
     * @param key the context key, must not be null
     * @param <T> the type of value stored under this key
     * @return the value, or null if not present
     */
    public <T> T get(final ContextKey<T> key) {
        Objects.requireNonNull(key, "Context key cannot be null");
        return switch (key) {
            case ContextKey.ClassKey<T>(var clazz) ->
                    (T) classBased.get(clazz);
            case ContextKey.StringKey<T>(var str, var type) ->
                    (T) stringBased.get(str);
            case ContextKey.DynamicKey<T>(var base, var type) ->
                    (T) stringBased.get(base);
        };
    }

    /**
     * Retrieves a required value by a type-safe key.
     * Throws IllegalStateException if the key is not present.
     *
     * @param key the context key, must not be null
     * @param <T> the type of value stored under this key
     * @return the value, never null
     * @throws IllegalStateException if the key is not present in the context
     */
    public <T> T getRequired(final ContextKey<T> key) {
        final T value = get(key);
        if (value == null) {
            throw new IllegalStateException("Required context attribute not found: " + key);
        }
        return value;
    }

    /**
     * Creates a new immutable context with a single key-value pair added or updated.
     * Uses pattern matching to route to the appropriate internal storage map.
     *
     * @param key the context key, must not be null
     * @param value the value to store
     * @param <T> the type of value
     * @return a new ComponentContext instance with the updated attribute
     */
    public <T> ComponentContext with(final ContextKey<T> key, final T value) {
        Objects.requireNonNull(key, "Context key cannot be null");
        return switch (key) {
            case ContextKey.ClassKey<T>(var clazz) -> {
                final Map<Class<?>, Object> newClassBased = new HashMap<>(classBased);
                newClassBased.put(clazz, value);
                yield new ComponentContext(newClassBased, stringBased);
            }
            case ContextKey.StringKey<T>(var str, var type) -> {
                final Map<String, Object> newStringBased = new HashMap<>(stringBased);
                newStringBased.put(str, value);
                yield new ComponentContext(classBased, newStringBased);
            }
            case ContextKey.DynamicKey<T>(var base, var type) -> {
                final Map<String, Object> newStringBased = new HashMap<>(stringBased);
                newStringBased.put(base, value);
                yield new ComponentContext(classBased, newStringBased);
            }
        };
    }


    /**
     * Retrieves a service/component by its class.
     *
     * @param clazz the class to look up, must not be null
     * @param <T> the type of the service/component
     * @return the instance, or null if not present
     */
    public <T> T get(final Class<T> clazz) {
        return get(new ContextKey.ClassKey<>(clazz));
    }

    /**
     * Retrieves a required service/component by its class.
     *
     * @param clazz the class to look up, must not be null
     * @param <T> the type of the service/component
     * @return the instance, never null
     * @throws IllegalStateException if not present in the context
     */
    public <T> T getRequired(final Class<T> clazz) {
        return getRequired(new ContextKey.ClassKey<>(clazz));
    }

    /**
     * Creates a new context with a service/component instance added.
     * Convenience method that wraps the class with a ClassKey.
     *
     * @param clazz the class serving as the key, must not be null
     * @param instance the instance to store
     * @param <T> the type of the service/component
     * @return a new ComponentContext instance with the service added
     */
    public <T> ComponentContext with(final Class<T> clazz, final T instance) {
        return with(new ContextKey.ClassKey<>(clazz), instance);
    }

    /**
     * Creates a new context with a service/component instance added.
     * Convenience method that wraps the class with a key represented by the exact class of the object.
     * @param instance the instance to store
     * @param <T> the type of the service/component
     * @return a new ComponentContext instance with the service added
     */
    public <T> ComponentContext with(final T instance) {
        final Class<Object> clazz = (Class<Object>) instance.getClass();
        return with(new ContextKey.ClassKey<>(clazz), instance);
    }

    /**
     * Creates a new context with a service/component instance added.
     * Convenience method that wraps the class with a key represented by the exact class of the object.
     * @param instances the instances to store
     * @return a new ComponentContext instance with the services added
     */
    public ComponentContext with(final List<Object> instances) {
        ComponentContext enrichedContext = this;
        for (Object service : instances) {
            Objects.requireNonNull(service, "Cannot be a null reference in a Services list");
            // Services are stored by their actual class, not by string key
            enrichedContext = enrichedContext.with(service);
        }
        return enrichedContext;
    }


    /**
     * Gets the device ID from the context.
     *
     * @return the device ID, or null if not present
     */
    public String deviceId() {
        return (String) stringBased.get(DEVICE_ID_KEY);
    }

    /**
     * Gets the session ID from the context.
     *
     * @return the session ID, or null if not present
     */
    public String sessionId() {
        return (String) stringBased.get(SESSION_ID_KEY);
    }


}
