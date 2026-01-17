package rsp.component;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Facade implementation of {@link Lookup} that delegates to existing infrastructure.
 *
 * <p>This class wraps:</p>
 * <ul>
 *   <li>{@link ComponentContext} - for data access (immutable)</li>
 *   <li>{@link CommandsEnqueue} - for event publishing (async via Reactor)</li>
 *   <li>{@link Subscriber} - for event subscription (component-scoped)</li>
 * </ul>
 *
 * <p><b>Immutability:</b> Calling {@code with()} creates a new ContextLookup
 * wrapping a new ComponentContext, but sharing the same event infrastructure.</p>
 *
 * <p><b>Thread safety:</b> Event publishing goes through the existing Reactor queue,
 * preserving the async threading model.</p>
 */
public final class ContextLookup implements Lookup {

    private final ComponentContext context;
    private final CommandsEnqueue commandsEnqueue;
    private final Subscriber subscriber;

    /**
     * Creates a new ContextLookup facade.
     *
     * @param context the component context for data access
     * @param commandsEnqueue for publishing events (async via Reactor)
     * @param subscriber for subscribing to events (component-scoped)
     */
    public ContextLookup(final ComponentContext context,
                         final CommandsEnqueue commandsEnqueue,
                         final Subscriber subscriber) {
        this.context = Objects.requireNonNull(context, "context");
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
        this.subscriber = Objects.requireNonNull(subscriber, "subscriber");
    }

    // ===== Data Access - delegates to ComponentContext =====

    @Override
    public <T> T get(final ContextKey<T> key) {
        return context.get(key);
    }

    @Override
    public <T> T get(final Class<T> clazz) {
        return context.get(clazz);
    }

    @Override
    public <T> T getRequired(final ContextKey<T> key) {
        return context.getRequired(key);
    }

    @Override
    public <T> T getRequired(final Class<T> clazz) {
        return context.getRequired(clazz);
    }

    // ===== Context Creation - returns new Lookup wrapping new context =====

    @Override
    public <T> Lookup with(final ContextKey<T> key, final T value) {
        return new ContextLookup(
            context.with(key, value),
            commandsEnqueue,
            subscriber
        );
    }

    @Override
    public <T> Lookup with(final Class<T> clazz, final T instance) {
        return new ContextLookup(
            context.with(clazz, instance),
            commandsEnqueue,
            subscriber
        );
    }

    // ===== Event Subscription - delegates to Subscriber (component-scoped) =====

    @Override
    public <T> Registration subscribe(final EventKey<T> key, final BiConsumer<String, T> handler) {
        subscriber.addEventHandler(key, handler);
        // Cleanup is handled by component lifecycle - handlers are GC'd with component
        return () -> { /* no-op: component lifecycle manages cleanup */ };
    }

    @Override
    public Registration subscribe(final EventKey.VoidKey key, final Runnable handler) {
        subscriber.addEventHandler(key, handler);
        return () -> { /* no-op: component lifecycle manages cleanup */ };
    }

    // ===== Event Publishing - delegates to CommandsEnqueue (async via Reactor) =====

    @Override
    public <T> void publish(final EventKey<T> key, final T payload) {
        // Only SimpleKey supports direct notification()
        // DynamicKey requires .with(extension) first to get a SimpleKey
        if (key instanceof EventKey.SimpleKey<T> simpleKey) {
            commandsEnqueue.offer(simpleKey.notification(payload));
        } else {
            throw new IllegalArgumentException(
                "Cannot publish with " + key.getClass().getSimpleName() +
                ". Use DynamicKey.with(extension) to get a SimpleKey first."
            );
        }
    }

    @Override
    public void publish(final EventKey.VoidKey key) {
        commandsEnqueue.offer(key.notification());
    }
}
