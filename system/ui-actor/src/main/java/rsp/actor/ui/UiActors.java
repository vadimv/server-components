package rsp.actor.ui;

import rsp.actor.ActorRef;
import rsp.actor.SendResult;
import rsp.component.ComponentSegment;
import rsp.component.StateUpdater;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Optional actor-to-component conveniences; {@code ui-core} has no actor dependency. */
public final class UiActors {
    private UiActors() {
    }

    /**
     * Subscribes a full-snapshot sink and binds its lifetime to this component
     * mount. A rejected subscription is already closed and is not registered.
     */
    public static <E, S, M> SendResult observe(
            ComponentSegment<S> segment,
            StateUpdater<S> updater,
            ActorRef<M> actor,
            BiFunction<S, E, S> projector,
            Function<ActorRef<E>, M> subscribe,
            Function<ActorRef<E>, M> unsubscribe) {
        Objects.requireNonNull(segment, "segment");
        UiActorBinding<E, S, M> binding = UiActorBinding.subscribe(
                actor, updater, projector, subscribe, unsubscribe);
        if (binding.admission() == SendResult.ACCEPTED) {
            segment.own(binding);
        }
        return binding.admission();
    }
}
