package rsp.component;

/**
 * Sealed interface for type-safe context attribute keys.
 *
 * <p>This interface uses sealed types to provide three distinct key variants,
 * each optimized for different use cases:</p>
 *
 * <ul>
 *   <li>{@link ClassKey} - ServiceLoader-style class-based keys for services and components</li>
 *   <li>{@link StringKey} - Namespace-based string keys with explicit type information</li>
 *   <li>{@link DynamicKey} - Builder pattern for parameterized keys (url.query.*, url.path.*)</li>
 * </ul>
 *
 * <p>The type parameter {@code <T>} specifies the <strong>VALUE type</strong> stored under this key,
 * not the key type itself. This enables type-safe retrieval without manual casting:</p>
 *
 * <pre>{@code
 * // ClassKey example - key identity is Router.class
 * ContextKey<Router> routerKey = new ContextKey.ClassKey<>(Router.class);
 * Router router = context.get(routerKey);  // No cast needed!
 *
 * // StringKey example - key identity is "route.pattern"
 * ContextKey<String> patternKey = new ContextKey.StringKey<>("route.pattern", String.class);
 * String pattern = context.get(patternKey);  // Type-safe!
 *
 * // DynamicKey example - key identity is "url.query.p"
 * ContextKey<String> queryKey = new ContextKey.DynamicKey<>("url.query", String.class).with("p");
 * String pageParam = context.get(queryKey);  // Type-safe!
 * }</pre>
 *
 * <p><strong>Storage Strategy:</strong> ClassKey and StringKey/DynamicKey use separate internal
 * storage maps in ComponentContext to prevent collisions between class-based and string-based keys.</p>
 *
 * @param <T> the type of value stored under this key
 */
public sealed interface ContextKey<T>
        permits ContextKey.ClassKey, ContextKey.StringKey, ContextKey.DynamicKey {

    /**
     * Returns the runtime type information for the value stored under this key.
     * Used for type-safe casting and validation.
     *
     * @return the Class object representing the value type
     */
    Class<T> type();

    /**
     * Class-based key variant using the ServiceLoader pattern.
     *
     * <p>Uses a {@code Class<?>} object as the key identity, enabling type-safe service lookup
     * similar to Java's ServiceLoader mechanism. This is the cleanest and most type-safe approach
     * for storing singleton services and components.</p>
     *
     * <p><strong>Storage:</strong> Stored in a separate {@code Map<Class<?>, Object>} to prevent
     * collisions with string-based keys. For example, {@code String.class} (ClassKey) and
     * {@code "java.lang.String"} (StringKey) are completely independent keys.</p>
     *
     * <p><strong>Usage example:</strong></p>
     * <pre>{@code
     * // Creating a ClassKey
     * ContextKey<Router> key = new ContextKey.ClassKey<>(Router.class);
     *
     * // Setting value in context
     * context.with(key, routerInstance);
     *
     * // Getting value from context (type-safe, no cast needed)
     * Router router = context.get(key);
     *
     * // Convenience shorthand (bypasses explicit ClassKey creation)
     * context.with(Router.class, routerInstance);
     * Router router = context.get(Router.class);
     * }</pre>
     *
     * @param <T> the type of value stored under this key
     * @param clazz the Class object serving as both key identity and type information
     */
    record ClassKey<T>(Class<T> clazz) implements ContextKey<T> {
        @Override
        public Class<T> type() {
            return clazz;
        }
    }

    /**
     * String-based key variant with explicit type information.
     *
     * <p>Uses a namespace string as the key identity, combined with explicit type information.
     * This is ideal for metadata attributes that don't have a natural class-based key.</p>
     *
     * <p><strong>Storage:</strong> Stored in {@code Map<String, Object>} along with DynamicKey.
     * The string value is used directly as the map key.</p>
     *
     * <p><strong>Naming convention:</strong> Use dot-separated namespaces for clarity:
     * {@code "route.pattern"}, {@code "list.page"}, {@code "auth.user"}</p>
     *
     * <p><strong>Usage example:</strong></p>
     * <pre>{@code
     * // Creating a StringKey (typically done in ContextKeys registry)
     * ContextKey<String> key = new ContextKey.StringKey<>("route.pattern", String.class);
     *
     * // Setting value in context
     * context.with(key, "/posts/:id");
     *
     * // Getting value from context (type-safe, no cast needed)
     * String pattern = context.get(key);
     * }</pre>
     *
     * @param <T> the type of value stored under this key
     * @param key the string identifier serving as the key identity
     * @param type the Class object representing the value type
     */
    record StringKey<T>(String key, Class<T> type) implements ContextKey<T> {
        // type() method inherited from record parameter
    }

    /**
     * Dynamic key variant using the builder pattern for parameterized keys.
     *
     * <p>Uses a base namespace combined with dynamic extensions to create parameterized keys.
     * This is ideal for key families like {@code url.query.*} or {@code url.path.*} where the
     * full key name is constructed at runtime.</p>
     *
     * <p><strong>Storage:</strong> Stored in {@code Map<String, Object>} along with StringKey.
     * The concatenated {@code baseKey + "." + extension} is used as the map key.</p>
     *
     * <p><strong>Usage example:</strong></p>
     * <pre>{@code
     * // Creating a DynamicKey base (typically done in ContextKeys registry)
     * ContextKey.DynamicKey<String> urlQuery = new ContextKey.DynamicKey<>("url.query", String.class);
     *
     * // Building specific keys with extensions
     * ContextKey<String> pageKey = urlQuery.with("p");       // "url.query.p"
     * ContextKey<String> sortKey = urlQuery.with("sort");    // "url.query.sort"
     *
     * // Setting values in context
     * context.with(pageKey, "3");
     * context.with(sortKey, "asc");
     *
     * // Getting values from context (type-safe, no cast needed)
     * String page = context.get(urlQuery.with("p"));         // "3"
     * String sort = context.get(urlQuery.with("sort"));      // "asc"
     * }</pre>
     *
     * @param <T> the type of value stored under this key
     * @param baseKey the base namespace for this key family
     * @param type the Class object representing the value type
     */
    record DynamicKey<T>(String baseKey, Class<T> type) implements ContextKey<T> {
        // type() method inherited from record parameter

        /**
         * Creates a new DynamicKey by appending an extension to the base key.
         *
         * <p>The extension is appended with a dot separator, so
         * {@code new DynamicKey<>("url.query", String.class).with("p")} produces
         * a key with identity {@code "url.query.p"}.</p>
         *
         * @param extension the string to append to the base key (should not contain leading dot)
         * @return a new DynamicKey with the extended key identity
         */
        public DynamicKey<T> with(String extension) {
            return new DynamicKey<>(baseKey + "." + extension, type);
        }
    }
}
