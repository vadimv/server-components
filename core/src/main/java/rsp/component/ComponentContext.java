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
 * <p><strong>Security Notice:</strong> This context may contain sensitive information, such as session identifiers
 * or device IDs. By design, this information is accessible to all components in the subtree.
 * It is assumed that all components running on the server are trusted. Developers must ensure that
 * sensitive context values are not accidentally rendered into the client-side HTML or leaked to untrusted parties.</p>
 */
public final class ComponentContext {

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
     * <p><strong>Runtime Type Safety:</strong> This method validates that the value's type
     * matches the key's declared type at runtime. This catches type mismatches that could
     * otherwise slip through due to Java's type erasure (e.g., sneaky casts, raw types).</p>
     *
     * @param key the context key, must not be null
     * @param value the value to store (null is allowed)
     * @param <T> the type of value
     * @return a new ComponentContext instance with the updated attribute
     * @throws IllegalArgumentException if value is non-null and doesn't match the key's type
     */
    public <T> ComponentContext with(final ContextKey<T> key, final T value) {
        Objects.requireNonNull(key, "Context key cannot be null");
        validateValueType(key, value);
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
     * Validates that a value matches the key's declared type at runtime.
     * This provides an additional safety net against type erasure exploits.
     *
     * @param key the context key with type information
     * @param value the value to validate (null values are allowed)
     * @param <T> the declared type
     * @throws IllegalArgumentException if value is non-null and doesn't match key's type
     */
    private <T> void validateValueType(final ContextKey<T> key, final T value) {
        if (value == null) {
            return; // null is always valid
        }
        final Class<T> expectedType = key.type();
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
                "Type mismatch for context key: expected " + expectedType.getName() +
                " but got " + value.getClass().getName() + " for key " + key
            );
        }
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
     * Convenience method that wraps the class with a class as a key.
     * @param instances the instances map to store
     * @return a new ComponentContext instance with the services added
     */
    public ComponentContext with(final Map<Class<?>, Object> instances) {
        Objects.requireNonNull(instances, "instances");
        ComponentContext enrichedContext = this;
        for (Map.Entry<Class<?>, Object> serviceEntry : instances.entrySet()) {
            Class<?> clazz = Objects.requireNonNull(serviceEntry.getKey(), "service class key");
            Object instance = Objects.requireNonNull(serviceEntry.getValue(), "service instance");
            if (!clazz.isInstance(instance)) {
                throw new IllegalArgumentException(
                    "Service instance type mismatch: expected " + clazz.getName() +
                    " but got " + instance.getClass().getName()
                );
            }
            @SuppressWarnings("unchecked")
            Class<Object> typedClass = (Class<Object>) clazz;
            enrichedContext = enrichedContext.with(new ContextKey.ClassKey<>(typedClass), instance);
        }
        return enrichedContext;
    }


}
