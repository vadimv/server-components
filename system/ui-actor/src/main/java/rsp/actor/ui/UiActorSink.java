package rsp.actor.ui;

import rsp.actor.ActorDeliveryException;
import rsp.actor.ActorEnvelope;
import rsp.actor.ActorRef;
import rsp.actor.ProcessingReceipt;
import rsp.actor.SendResult;
import rsp.component.StateUpdater;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * Mount-owned local actor reference that projects the latest full snapshot onto
 * a component's event loop. Intermediate snapshots may be dropped under load.
 * Close it before sending an unsubscribe command; {@link UiActorBinding} manages
 * this ordering for mount-owned subscriptions.
 */
public final class UiActorSink<E, S> implements ActorRef<E>, AutoCloseable {
    private final StateUpdater<S> updater;
    private final BiFunction<S, E, S> project;
    private final AtomicReference<ActorEnvelope<E>> latest = new AtomicReference<>();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private UiActorSink(StateUpdater<S> updater, BiFunction<S, E, S> project) {
        this.updater = Objects.requireNonNull(updater, "updater");
        this.project = Objects.requireNonNull(project, "project");
    }

    /** Use only for self-contained snapshots, not deltas or events that must all be observed. */
    public static <E, S> UiActorSink<E, S> latest(StateUpdater<S> updater,
                                                   BiFunction<S, E, S> project) {
        return new UiActorSink<>(updater, project);
    }

    @Override
    public SendResult tell(ActorEnvelope<E> envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (closed.get()) {
            return SendResult.STOPPED;
        }
        latest.set(envelope);
        if (closed.get()) {
            latest.set(null);
            return SendResult.STOPPED;
        }
        try {
            enqueue();
            return SendResult.ACCEPTED;
        } catch (RuntimeException rejectedByPage) {
            close();
            return SendResult.STOPPED;
        }
    }

    @Override
    public ProcessingReceipt track(ActorEnvelope<E> envelope) {
        SendResult result = tell(envelope);
        return new ProcessingReceipt(result, result == SendResult.ACCEPTED
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(new ActorDeliveryException(result)));
    }

    private void enqueue() {
        if (!queued.compareAndSet(false, true)) {
            return;
        }
        try {
            updater.applyStateTransformation(state -> {
                ActorEnvelope<E> envelope = latest.getAndSet(null);
                queued.set(false);
                if (!closed.get() && latest.get() != null) {
                    enqueue();
                }
                if (closed.get() || envelope == null) {
                    return state;
                }
                return Objects.requireNonNull(project.apply(state, envelope.message()), "projected UI state");
            });
        } catch (RuntimeException | Error failure) {
            queued.set(false);
            latest.set(null);
            throw failure;
        }
    }

    @Override
    public void close() {
        closed.set(true);
        latest.set(null);
    }
}
