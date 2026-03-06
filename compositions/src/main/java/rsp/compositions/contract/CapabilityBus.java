package rsp.compositions.contract;

import rsp.component.EventKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Synchronous publish-subscribe bus for capability negotiation between sibling contracts.
 * <p>
 * Created by {@link SceneBuilder} before contract instantiation, passed through context.
 * Contracts publish and subscribe to capabilities in their constructors.
 * After all contracts are instantiated, {@link #resolve()} delivers all published
 * capabilities to matching subscribers synchronously — before the view is rendered.
 * <p>
 * This ensures capability-dependent UI is correct on the first render (no flicker).
 * For runtime capability changes (e.g., after SET_PRIMARY), contracts use the regular
 * async event pipeline with the same {@link EventKey} types.
 */
public final class CapabilityBus {

    private final List<Entry<?>> published = new ArrayList<>();
    private final List<Subscription<?>> subscriptions = new ArrayList<>();
    private boolean resolved;

    public <T> void publish(EventKey<T> key, T value) {
        if (resolved) {
            throw new IllegalStateException("CapabilityBus already resolved; use async events for runtime changes");
        }
        published.add(new Entry<>(key, value));
    }

    public <T> void subscribe(EventKey<T> key, Consumer<T> handler) {
        if (resolved) {
            throw new IllegalStateException("CapabilityBus already resolved; use async events for runtime changes");
        }
        subscriptions.add(new Subscription<>(key, handler));
    }

    /**
     * Deliver all published capabilities to matching subscribers.
     * Each subscriber receives the value from every matching publication.
     * After this call, no further publish/subscribe is allowed.
     */
    public void resolve() {
        resolved = true;
        for (final Entry<?> entry : published) {
            for (final Subscription<?> sub : subscriptions) {
                if (sub.key.name().equals(entry.key.name())) {
                    deliverUnchecked(entry, sub);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void deliverUnchecked(Entry<?> entry, Subscription<?> sub) {
        ((Subscription<T>) sub).handler.accept(((Entry<T>) entry).value);
    }

    private record Entry<T>(EventKey<T> key, T value) {}

    private record Subscription<T>(EventKey<T> key, Consumer<T> handler) {}
}
