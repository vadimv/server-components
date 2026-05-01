package rsp.component;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Unified lookup interface for context data and events.
 *
 * <p>This is the <b>primary injection point</b> for components, replacing
 * direct injection of {@link ComponentContext}, {@link Subscriber}, and {@link CommandsEnqueue}.</p>
 *
 * <p><b>Immutability model:</b></p>
 * <ul>
 *   <li>Data: immutable - {@code with()} returns new instance</li>
 *   <li>Events: shared channel - {@code publish()} uses same instance</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * public EditViewContract(Lookup lookup) {
 *     // Service lookup
 *     Router router = lookup.get(Router.class);
 *
 *     // Context value lookup
 *     String pattern = lookup.get(ContextKeys.ROUTE_PATTERN);
 *
 *     // Event subscription
 *     lookup.subscribe(FORM_SUBMITTED, (name, data) -> save(data));
 *
 *     // Event publishing
 *     lookup.publish(FORM_SUBMITTED, formData);
 * }
 * }</pre>
 *
 * @see ContextLookup for the facade implementation
 */
public interface Lookup {

    // ===== Data Access =====

    /**
     * Retrieves a value by a type-safe key.
     *
     * @param key the context key
     * @param <T> the type of value
     * @return the value, or null if not present
     */
    <T> T get(ContextKey<T> key);

    /**
     * Retrieves a service/component by its class.
     *
     * @param clazz the class to look up
     * @param <T> the type of the service/component
     * @return the instance, or null if not present
     */
    <T> T get(Class<T> clazz);

    /**
     * Retrieves a required value by a type-safe key.
     *
     * @param key the context key
     * @param <T> the type of value
     * @return the value, never null
     * @throws IllegalStateException if not present
     */
    <T> T getRequired(ContextKey<T> key);

    /**
     * Retrieves a required service/component by its class.
     *
     * @param clazz the class to look up
     * @param <T> the type of the service/component
     * @return the instance, never null
     * @throws IllegalStateException if not present
     */
    <T> T getRequired(Class<T> clazz);

    // ===== Config Access (String-based keys with type conversion) =====

    /**
     * Retrieves a config value as a String by its dot-separated key.
     *
     * @param key the property key (e.g., "list.defaultPageSize")
     * @return the value, or null if not present
     */
    default String getString(final String key) {
        return get(new ContextKey.StringKey<>(key, String.class));
    }

    /**
     * Retrieves a config value as a String with a default.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present
     * @return the value, or defaultValue if not present
     */
    default String getString(final String key, final String defaultValue) {
        final String value = getString(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Retrieves a config value parsed as an int.
     * Returns the default if the key is absent or not parseable.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present or not parseable
     * @return the parsed int value, or defaultValue
     */
    default int getInt(final String key, final int defaultValue) {
        final String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Retrieves a required config value parsed as an int.
     * Throws if the key is absent or the value is not a valid integer.
     *
     * @param key the property key
     * @return the parsed int value
     * @throws IllegalArgumentException if the key is absent or the value is not a valid integer
     */
    default int getRequiredInt(final String key) {
        final String value = getString(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required config property '" + key + "' is not set");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Config property '" + key + "' has invalid integer value: '" + value + "'", e);
        }
    }

    /**
     * Retrieves a config value parsed as a boolean.
     * Returns the default if the key is absent or not parseable.
     *
     * @param key the property key
     * @param defaultValue fallback if key is not present or not parseable
     * @return the parsed boolean value, or defaultValue
     */
    default boolean getBoolean(final String key, final boolean defaultValue) {
        final String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /**
     * Retrieves a required config value parsed as a boolean.
     * Throws if the key is absent or the value is not "true"/"false".
     *
     * @param key the property key
     * @return the parsed boolean value
     * @throws IllegalArgumentException if the key is absent or the value is not "true" or "false"
     */
    default boolean getRequiredBoolean(final String key) {
        final String value = getString(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Required config property '" + key + "' is not set");
        }
        final String trimmed = value.trim().toLowerCase();
        if ("true".equals(trimmed)) {
            return true;
        }
        if ("false".equals(trimmed)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Config property '" + key + "' has invalid boolean value: '" + value + "' (expected 'true' or 'false')");
    }

    // ===== Context Creation (returns new instance) =====

    /**
     * Creates a new Lookup with a value added.
     * The original Lookup is unchanged (immutable).
     *
     * @param key the context key
     * @param value the value to store
     * @param <T> the type of value
     * @return a new Lookup instance with the value added
     */
    <T> Lookup with(ContextKey<T> key, T value);

    /**
     * Creates a new Lookup with a service/component instance added.
     * The original Lookup is unchanged (immutable).
     *
     * @param clazz the class serving as the key
     * @param instance the instance to store
     * @param <T> the type of the service/component
     * @return a new Lookup instance with the service added
     */
    <T> Lookup with(Class<T> clazz, T instance);

    // ===== Context Observation =====

    /**
     * Watch a context key for changes on this lookup's owning segment.
     * <p>
     * The handler is invoked when the segment is reused with a new
     * {@link ComponentContext} and the watched value changes according to
     * {@link java.util.Objects#equals(Object, Object)}.
     *
     * @param key the context key to observe
     * @param handler receives the old and new values
     * @param <T> the value type
     * @return registration handle for explicit unsubscribe
     * @throws UnsupportedOperationException if this lookup is not backed by a live context scope
     */
    default <T> Registration watch(final ContextKey<T> key, final BiConsumer<T, T> handler) {
        throw new UnsupportedOperationException("Context watching not available in this lookup");
    }

    /**
     * Watch a context key for changes and receive the new value only.
     *
     * @param key the context key to observe
     * @param handler receives the new value
     * @param <T> the value type
     * @return registration handle for explicit unsubscribe
     * @throws UnsupportedOperationException if this lookup is not backed by a live context scope
     */
    default <T> Registration watch(final ContextKey<T> key, final Consumer<T> handler) {
        return watch(key, (_, newValue) -> handler.accept(newValue));
    }

    // ===== Event Subscription =====

    /**
     * Subscribe to events with a typed payload.
     *
     * <p>Handler is registered with component-scoped lifecycle.
     * Cleanup is automatic when component unmounts.</p>
     *
     * @param key the typed event key
     * @param handler receives the event name and typed payload
     * @param <T> the payload type
     * @return registration handle (unsubscribe typically not needed)
     */
    <T> Registration subscribe(EventKey<T> key, BiConsumer<String, T> handler);

    /**
     * Subscribe to void events (no payload).
     *
     * <p>Handler is registered with component-scoped lifecycle.
     * Cleanup is automatic when component unmounts.</p>
     *
     * @param key the void event key
     * @param handler the handler to invoke
     * @return registration handle (unsubscribe typically not needed)
     */
    Registration subscribe(EventKey.VoidKey key, Runnable handler);

    // ===== Task Enqueueing =====

    /**
     * Enqueue a task to run on the event loop.
     *
     * <p>The task is queued via the Reactor and executes after any
     * previously queued events have been processed. This provides
     * a synchronization point: a task enqueued after an event publish
     * is guaranteed to run after that event's handler completes.</p>
     *
     * @param task the task to execute on the event loop
     * @throws UnsupportedOperationException if this Lookup does not support task enqueueing
     */
    default void enqueueTask(final Runnable task) {
        throw new UnsupportedOperationException("Task enqueueing not available in this context");
    }

    // ===== Event Publishing =====

    /**
     * Publish an event with a typed payload.
     *
     * <p>Event is delivered asynchronously via Reactor queue.</p>
     *
     * @param key the typed event key
     * @param payload the event payload
     * @param <T> the payload type
     */
    <T> void publish(EventKey<T> key, T payload);

    /**
     * Publish a void event (no payload).
     *
     * <p>Event is delivered asynchronously via Reactor queue.</p>
     *
     * @param key the void event key
     */
    void publish(EventKey.VoidKey key);

    /**
     * Registration handle for event subscriptions.
     *
     * <p>Unsubscribe is typically not needed since handlers are
     * automatically cleaned up with component lifecycle.</p>
     */
    interface Registration {
        /**
         * Unsubscribe the handler.
         * Usually not needed - component lifecycle handles cleanup.
         */
        void unsubscribe();
    }
}
