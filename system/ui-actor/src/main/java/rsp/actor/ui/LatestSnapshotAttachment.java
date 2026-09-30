package rsp.actor.ui;

import rsp.actor.runtime.ActorSnapshot;
import rsp.actor.runtime.ActorScheduler;
import rsp.actor.runtime.TrampolineExecutor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** One pending snapshot and one timer/queued render per mounted view. */
final class LatestSnapshotAttachment<S> implements Consumer<ActorSnapshot<S>>, AutoCloseable {
    private final ActorRenderPolicy policy;
    private final Executor page;
    private final Consumer<ActorSnapshot<S>> render;
    private ActorSnapshot<S> latest;
    private long highestRevision;
    private long lastRender;
    private Work pending;
    private boolean closed;

    LatestSnapshotAttachment(ActorSnapshot<S> initial, ActorRenderPolicy policy,
                             Executor page, Consumer<ActorSnapshot<S>> render) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.page = new TrampolineExecutor(Objects.requireNonNull(page, "page"));
        this.render = Objects.requireNonNull(render, "render");
        highestRevision = Objects.requireNonNull(initial, "initial").revision();
        lastRender = policy.nanoTime().getAsLong();
    }

    @Override
    public void accept(ActorSnapshot<S> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        synchronized (this) {
            if (closed || snapshot.revision() <= highestRevision) return;
            highestRevision = snapshot.revision();
            latest = snapshot;
        }
        schedule();
    }

    private void schedule() {
        final Work work;
        final long delay;
        synchronized (this) {
            if (closed || latest == null || pending != null) return;
            work = new Work();
            pending = work;
            delay = Math.max(0, policy.minimumInterval().toNanos()
                    - (policy.nanoTime().getAsLong() - lastRender));
        }
        try {
            if (delay == 0) {
                enqueue(work);
            } else {
                work.setCancellation(policy.scheduler().schedule(Duration.ofNanos(delay), () -> enqueue(work)));
            }
        } catch (RejectedExecutionException stopped) {
            // Application shutdown can stop its scheduler before the page consumes unmount.
            close();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private void enqueue(Work work) {
        synchronized (this) {
            if (closed || pending != work || work.queued) return;
            work.queued = true;
        }
        try {
            page.execute(() -> render(work));
        } catch (RejectedExecutionException stopped) {
            // Application shutdown can stop its scheduler before the page consumes unmount.
            close();
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private void render(Work work) {
        final ActorSnapshot<S> snapshot;
        synchronized (this) {
            if (closed || pending != work) return;
            snapshot = latest;
            latest = null;
            lastRender = policy.nanoTime().getAsLong();
        }
        try {
            render.accept(snapshot);
        } catch (RuntimeException | Error failure) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Actor snapshot render failed [failureType=" + failure.getClass().getName() + "]");
        } finally {
            synchronized (this) {
                if (pending == work) pending = null;
            }
            work.cancel();
            schedule();
        }
    }

    @Override
    public void close() {
        final Work cancelling;
        synchronized (this) {
            closed = true;
            latest = null;
            cancelling = pending;
            pending = null;
        }
        if (cancelling != null) cancelling.cancel();
    }

    private static final class Work {
        private static final ActorScheduler.Cancellation CANCELLED = () -> { };
        private final AtomicReference<ActorScheduler.Cancellation> cancellation = new AtomicReference<>();
        private boolean queued;

        void setCancellation(ActorScheduler.Cancellation handle) {
            Objects.requireNonNull(handle, "cancellation");
            if (!cancellation.compareAndSet(null, handle)) handle.cancel();
        }

        void cancel() {
            var handle = cancellation.getAndSet(CANCELLED);
            if (handle != null && handle != CANCELLED) handle.cancel();
        }
    }
}
