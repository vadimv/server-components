package rsp.actor.ui;

import rsp.actor.ActorRef;
import rsp.actor.SendResult;
import rsp.component.StateUpdater;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/** One component mount's subscription to full actor snapshots. */
public final class UiActorBinding<E, S, M> implements AutoCloseable {
    private final ActorRef<M> actor;
    private final UiActorSink<E, S> sink;
    private final Function<ActorRef<E>, M> unsubscribe;
    private final SendResult admission;
    private final AtomicBoolean closed = new AtomicBoolean();

    private UiActorBinding(final ActorRef<M> actor,
                           final StateUpdater<S> updater,
                           final BiFunction<S, E, S> projector,
                           final Function<ActorRef<E>, M> subscribe,
                           final Function<ActorRef<E>, M> unsubscribe) {
        this.actor = Objects.requireNonNull(actor);
        this.sink = UiActorSink.latest(updater, projector);
        this.unsubscribe = Objects.requireNonNull(unsubscribe);
        try {
            this.admission = actor.tell(Objects.requireNonNull(subscribe).apply(sink));
        } catch (RuntimeException | Error failure) {
            sink.close();
            throw failure;
        }
        if (admission != SendResult.ACCEPTED) {
            sink.close();
            closed.set(true);
        }
    }

    public static <E, S, M> UiActorBinding<E, S, M> subscribe(
            final ActorRef<M> actor,
            final StateUpdater<S> updater,
            final BiFunction<S, E, S> projector,
            final Function<ActorRef<E>, M> subscribe,
            final Function<ActorRef<E>, M> unsubscribe) {
        return new UiActorBinding<>(actor, updater, projector, subscribe, unsubscribe);
    }

    /** Admission of the initial subscription; a rejected binding is already closed. */
    public SendResult admission() {
        return admission;
    }

    public SendResult tell(final M message) {
        return closed.get() ? SendResult.STOPPED : actor.tell(message);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            sink.close();
            actor.tell(unsubscribe.apply(sink));
        }
    }
}
