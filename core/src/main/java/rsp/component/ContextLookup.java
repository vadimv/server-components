package rsp.component;

import rsp.page.events.GenericTaskEvent;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

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
 * <p><b>Lazy context resolution:</b> The lookup may be constructed with a
 * {@link Supplier} that resolves the {@link ComponentContext} on each
 * {@link #get} call. This allows the lookup to track a segment's current
 * context across reuse — when the framework updates a reused segment's context,
 * subsequent reads through this lookup see the new value automatically.
 * The fixed-context constructor wraps its argument in a constant supplier and
 * preserves the original (snapshot) behavior.</p>
 *
 * <p><b>{@code with()} snapshot semantics:</b> Calling {@code with(key, value)}
 * captures the current context once and returns a Lookup whose supplier always
 * returns that captured-extended context. The derived lookup is therefore frozen
 * to the moment {@code with()} was called, even if the original lookup is lazy.
 * This matches existing caller expectations: a derived lookup is a stable
 * extension of <em>what was current</em>, not of <em>whatever happens to be
 * current later</em>.</p>
 *
 * <p><b>Thread safety:</b> Event publishing goes through the existing Reactor queue,
 * preserving the async threading model.</p>
 */
public final class ContextLookup implements Lookup {

    private final Supplier<ComponentContext> contextSupplier;
    private final ContextScope contextScope;
    private final CommandsEnqueue commandsEnqueue;
    private final Subscriber subscriber;

    /**
     * Creates a new ContextLookup facade with a lazy context supplier.
     *
     * @param contextSupplier resolves the component context on each read.
     *                        May return a different context on each call,
     *                        e.g. a segment's mutable {@code componentContext}.
     * @param commandsEnqueue for publishing events (async via Reactor)
     * @param subscriber for subscribing to events (component-scoped)
     */
    public ContextLookup(final Supplier<ComponentContext> contextSupplier,
                         final CommandsEnqueue commandsEnqueue,
                         final Subscriber subscriber) {
        this.contextSupplier = Objects.requireNonNull(contextSupplier, "contextSupplier");
        this.contextScope = null;
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
        this.subscriber = Objects.requireNonNull(subscriber, "subscriber");
    }

    /**
     * Creates a lookup backed by a live context scope owned by a segment.
     * Reads follow the scope's current context and {@link #watch(ContextKey, java.util.function.BiConsumer)}
     * observes future scope replacements.
     *
     * @param contextScope live context scope for a component segment
     * @param commandsEnqueue for publishing events (async via Reactor)
     * @param subscriber for subscribing to events (component-scoped)
     */
    public ContextLookup(final ContextScope contextScope,
                         final CommandsEnqueue commandsEnqueue,
                         final Subscriber subscriber) {
        this.contextScope = Objects.requireNonNull(contextScope, "contextScope");
        this.contextSupplier = contextScope::current;
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue, "commandsEnqueue");
        this.subscriber = Objects.requireNonNull(subscriber, "subscriber");
    }

    /**
     * Creates a new ContextLookup facade with a fixed context (snapshot behavior).
     * The lookup is backed by a private {@link ContextScope}; reads are fixed unless
     * that private scope is replaced internally, which normal callers cannot do.
     *
     * @param context the component context for data access
     * @param commandsEnqueue for publishing events (async via Reactor)
     * @param subscriber for subscribing to events (component-scoped)
     */
    public ContextLookup(final ComponentContext context,
                         final CommandsEnqueue commandsEnqueue,
                         final Subscriber subscriber) {
        this(new ContextScope(Objects.requireNonNull(context, "context")),
             commandsEnqueue,
             subscriber);
    }

    // ===== Data Access - delegates to ComponentContext =====

    @Override
    public <T> T get(final ContextKey<T> key) {
        return contextSupplier.get().get(key);
    }

    @Override
    public <T> T get(final Class<T> clazz) {
        return contextSupplier.get().get(clazz);
    }

    @Override
    public <T> T getRequired(final ContextKey<T> key) {
        return contextSupplier.get().getRequired(key);
    }

    @Override
    public <T> T getRequired(final Class<T> clazz) {
        return contextSupplier.get().getRequired(clazz);
    }

    // ===== Context Creation - returns new Lookup wrapping new context =====
    //
    // with() snapshots the current context at-call-time. The derived lookup is
    // frozen to that moment — see class javadoc.

    @Override
    public <T> Lookup with(final ContextKey<T> key, final T value) {
        return new ContextLookup(
            contextSupplier.get().with(key, value),
            commandsEnqueue,
            subscriber
        );
    }

    @Override
    public <T> Lookup with(final Class<T> clazz, final T instance) {
        return new ContextLookup(
            contextSupplier.get().with(clazz, instance),
            commandsEnqueue,
            subscriber
        );
    }

    // ===== Context Observation - delegates to ContextScope when available =====

    @Override
    public <T> Registration watch(final ContextKey<T> key,
                                  final java.util.function.BiConsumer<T, T> handler) {
        if (contextScope == null) {
            throw new UnsupportedOperationException("Context watching requires a ContextScope-backed lookup");
        }
        return contextScope.watch(key, handler);
    }

    // ===== Event Subscription - delegates to Subscriber (component-scoped) =====

    @Override
    public <T> Registration subscribe(final EventKey<T> key, final BiConsumer<String, T> handler) {
        return subscriber.registerEventHandler(key, handler, false);
    }

    @Override
    public Registration subscribe(final EventKey.VoidKey key, final Runnable handler) {
        return subscriber.registerEventHandler(key, handler, false);
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

    // ===== Task Enqueueing - delegates to CommandsEnqueue (async via Reactor) =====

    @Override
    public void enqueueTask(final Runnable task) {
        commandsEnqueue.offer(new GenericTaskEvent(task));
    }
}
