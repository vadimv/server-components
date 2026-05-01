package rsp.component;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Mutable context cell owned by a single {@link ComponentSegment}.
 * <p>
 * {@link ComponentContext} remains an immutable snapshot. A scope keeps the
 * current snapshot for a live segment and owns that segment's context watchers.
 * When reconciliation reuses the segment with a new parent-provided context,
 * the framework replaces the snapshot and notifies watchers whose keys changed.
 * All methods must be called from the page event-loop thread.
 */
public final class ContextScope {

    private ComponentContext current;
    private final Map<ContextKey<?>, List<Watcher<?>>> watchers = new HashMap<>();

    public ContextScope(final ComponentContext initialContext) {
        this.current = Objects.requireNonNull(initialContext, "initialContext");
    }

    public ComponentContext current() {
        return current;
    }

    public <T> Lookup.Registration watch(final ContextKey<T> key,
                                         final BiConsumer<T, T> handler) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(handler, "handler");

        final Watcher<T> watcher = new Watcher<>(key, handler);
        watchers.computeIfAbsent(key, _ -> new ArrayList<>()).add(watcher);
        return () -> removeWatcher(key, watcher);
    }

    void replace(final ComponentContext next) {
        Objects.requireNonNull(next, "next");
        final ComponentContext previous = current;
        current = next;

        if (watchers.isEmpty()) {
            return;
        }

        final List<ContextKey<?>> keys = new ArrayList<>(watchers.keySet());
        for (final ContextKey<?> key : keys) {
            final Object oldValue = previous.get(castKey(key));
            final Object newValue = next.get(castKey(key));
            if (!Objects.equals(oldValue, newValue)) {
                notifyWatchers(key, oldValue, newValue);
            }
        }
    }

    void clear() {
        watchers.clear();
    }

    private <T> void removeWatcher(final ContextKey<T> key, final Watcher<T> watcher) {
        final List<Watcher<?>> registered = watchers.get(key);
        if (registered == null) {
            return;
        }
        registered.remove(watcher);
        if (registered.isEmpty()) {
            watchers.remove(key);
        }
    }

    private void notifyWatchers(final ContextKey<?> key,
                                final Object oldValue,
                                final Object newValue) {
        final List<Watcher<?>> registered = watchers.get(key);
        if (registered == null || registered.isEmpty()) {
            return;
        }
        for (final Watcher<?> watcher : new ArrayList<>(registered)) {
            watcher.notifyUnchecked(oldValue, newValue);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ContextKey<T> castKey(final ContextKey<?> key) {
        return (ContextKey<T>) key;
    }

    private record Watcher<T>(ContextKey<T> key, BiConsumer<T, T> handler) {
        @SuppressWarnings("unchecked")
        void notifyUnchecked(final Object oldValue, final Object newValue) {
            handler.accept((T) oldValue, (T) newValue);
        }
    }
}
