package rsp.app.posts.components;

import rsp.component.ContextKey;
import rsp.component.EventKey;
import rsp.component.Lookup;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Test implementation of {@link Lookup} for unit testing contracts and components.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>In-memory data storage</li>
 *   <li>Synchronous event dispatch (for predictable testing)</li>
 *   <li>Published event tracking</li>
 *   <li>Fluent API for test setup</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Test
 * void form_submission_triggers_save() {
 *     TestLookup lookup = new TestLookup()
 *         .withData(Router.class, mockRouter);
 *
 *     AtomicBoolean saved = new AtomicBoolean(false);
 *     lookup.subscribe(FORM_SUBMITTED, (name, data) -> saved.set(true));
 *
 *     lookup.publish(FORM_SUBMITTED, formData);
 *
 *     assertTrue(saved.get());
 *     assertTrue(lookup.wasPublished(FORM_SUBMITTED));
 * }
 * }</pre>
 */
public class TestLookup implements Lookup {

    private final Map<Object, Object> data = new HashMap<>();
    private final List<PublishedEvent<?>> publishedEvents = new ArrayList<>();
    private final Map<String, List<BiConsumer<String, ?>>> handlers = new HashMap<>();
    private final Map<String, List<Runnable>> voidHandlers = new HashMap<>();

    // ===== Data Access =====

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final ContextKey<T> key) {
        return (T) data.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(final Class<T> clazz) {
        return (T) data.get(clazz);
    }

    @Override
    public <T> T getRequired(final ContextKey<T> key) {
        final T value = get(key);
        if (value == null) {
            throw new IllegalStateException("Required context attribute not found: " + key);
        }
        return value;
    }

    @Override
    public <T> T getRequired(final Class<T> clazz) {
        final T value = get(clazz);
        if (value == null) {
            throw new IllegalStateException("Required service not found: " + clazz.getName());
        }
        return value;
    }

    // ===== Context Creation =====

    @Override
    public <T> Lookup with(final ContextKey<T> key, final T value) {
        final TestLookup newLookup = copy();
        newLookup.data.put(key, value);
        return newLookup;
    }

    @Override
    public <T> Lookup with(final Class<T> clazz, final T instance) {
        final TestLookup newLookup = copy();
        newLookup.data.put(clazz, instance);
        return newLookup;
    }

    // ===== Event Subscription =====

    @Override
    public <T> Registration subscribe(final EventKey<T> key, final BiConsumer<String, T> handler) {
        handlers.computeIfAbsent(key.name(), k -> new ArrayList<>())
                .add((BiConsumer<String, ?>) handler);
        return () -> handlers.get(key.name()).remove(handler);
    }

    @Override
    public Registration subscribe(final EventKey.VoidKey key, final Runnable handler) {
        voidHandlers.computeIfAbsent(key.name(), k -> new ArrayList<>()).add(handler);
        return () -> voidHandlers.get(key.name()).remove(handler);
    }

    // ===== Event Publishing =====

    @Override
    public <T> void publish(final EventKey<T> key, final T payload) {
        publishedEvents.add(new PublishedEvent<>(key.name(), payload));
        // Dispatch synchronously for predictable testing
        dispatchEvent(key.name(), payload);
    }

    @Override
    public void publish(final EventKey.VoidKey key) {
        publishedEvents.add(new PublishedEvent<>(key.name(), null));
        dispatchVoidEvent(key.name());
    }

    // ===== Task Enqueueing =====

    @Override
    public void enqueueTask(final Runnable task) {
        task.run();
    }

    // ===== Test Setup Utilities (Fluent API) =====

    /**
     * Add data to this lookup for test setup.
     *
     * @param key the context key
     * @param value the value
     * @param <T> the value type
     * @return this lookup for chaining
     */
    public <T> TestLookup withData(final ContextKey<T> key, final T value) {
        data.put(key, value);
        return this;
    }

    /**
     * Add a service to this lookup for test setup.
     *
     * @param clazz the service class
     * @param instance the service instance
     * @param <T> the service type
     * @return this lookup for chaining
     */
    public <T> TestLookup withData(final Class<T> clazz, final T instance) {
        data.put(clazz, instance);
        return this;
    }

    // ===== Test Assertion Utilities =====

    /**
     * Get all published events for assertions.
     *
     * @return unmodifiable list of published events
     */
    public List<PublishedEvent<?>> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    /**
     * Check if an event with the given name was published.
     *
     * @param eventName the event name
     * @return true if published
     */
    public boolean wasPublished(final String eventName) {
        return publishedEvents.stream().anyMatch(e -> e.name().equals(eventName));
    }

    /**
     * Check if an event with the given key was published.
     *
     * @param key the event key
     * @return true if published
     */
    public boolean wasPublished(final EventKey<?> key) {
        return wasPublished(key.name());
    }

    /**
     * Check if a void event with the given key was published.
     *
     * @param key the void event key
     * @return true if published
     */
    public boolean wasPublished(final EventKey.VoidKey key) {
        return wasPublished(key.name());
    }

    /**
     * Get the payload of the last published event with the given name.
     *
     * @param eventName the event name
     * @param <T> the expected payload type
     * @return the payload, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getLastPublishedPayload(final String eventName) {
        for (int i = publishedEvents.size() - 1; i >= 0; i--) {
            final PublishedEvent<?> event = publishedEvents.get(i);
            if (event.name().equals(eventName)) {
                return (T) event.payload();
            }
        }
        return null;
    }

    /**
     * Get the payload of the last published event with the given key.
     *
     * @param key the event key
     * @param <T> the payload type
     * @return the payload, or null if not found
     */
    public <T> T getLastPublishedPayload(final EventKey<T> key) {
        return getLastPublishedPayload(key.name());
    }

    /**
     * Clear all published events (for test isolation).
     */
    public void clearPublishedEvents() {
        publishedEvents.clear();
    }

    // ===== Internal =====

    private TestLookup copy() {
        final TestLookup copy = new TestLookup();
        copy.data.putAll(this.data);
        copy.handlers.putAll(this.handlers);
        copy.voidHandlers.putAll(this.voidHandlers);
        // Don't copy published events - new lookup starts fresh
        return copy;
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatchEvent(final String name, final T payload) {
        final List<BiConsumer<String, ?>> eventHandlers = handlers.get(name);
        if (eventHandlers != null) {
            for (final BiConsumer<String, ?> handler : new ArrayList<>(eventHandlers)) {
                ((BiConsumer<String, T>) handler).accept(name, payload);
            }
        }
    }

    private void dispatchVoidEvent(final String name) {
        final List<Runnable> eventHandlers = voidHandlers.get(name);
        if (eventHandlers != null) {
            for (final Runnable handler : new ArrayList<>(eventHandlers)) {
                handler.run();
            }
        }
    }

    /**
     * Record of a published event for test assertions.
     *
     * @param name the event name
     * @param payload the event payload (null for void events)
     * @param <T> the payload type
     */
    public record PublishedEvent<T>(String name, T payload) {}
}
