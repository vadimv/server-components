package rsp.actor.ui;

import org.junit.jupiter.api.Test;
import rsp.actor.runtime.ActorScheduler;
import rsp.actor.runtime.ActorSnapshot;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LatestSnapshotAttachmentTests {
    @Test
    void fastProducerRetainsOneTimerAndRendersNewestAtExecutionTime() {
        Clock clock = new Clock();
        Queue<Runnable> page = new ArrayDeque<>();
        List<Long> rendered = new ArrayList<>();
        var attachment = new LatestSnapshotAttachment<>(snapshot(0), clock.policy(), page::add,
                state -> rendered.add(state.revision()));
        for (int i = 1; i <= 10_000; i++) attachment.accept(snapshot(i));
        assertEquals(1, clock.tasks.size());
        assertTrue(page.isEmpty());
        clock.advance(49);
        assertTrue(page.isEmpty());
        clock.advance(1);
        assertEquals(1, page.size());
        for (int i = 10_001; i <= 20_000; i++) attachment.accept(snapshot(i));
        assertEquals(1, page.size());
        page.remove().run();
        assertEquals(List.of(20_000L), rendered);
        assertTrue(clock.tasks.isEmpty());
        assertTrue(page.isEmpty());
        attachment.accept(snapshot(20_001));
        clock.advance(50);
        page.remove().run();
        assertEquals(List.of(20_000L, 20_001L), rendered); // Last update is delivered without another commit.
        assertTrue(clock.tasks.isEmpty());
    }

    @Test
    void oldInitialCallbackCannotOverwriteANewerPendingOrRenderedRevision() {
        Queue<Runnable> page = new ArrayDeque<>();
        List<Long> seen = new ArrayList<>();
        var attachment = new LatestSnapshotAttachment<>(snapshot(10), ActorRenderPolicy.immediate(),
                page::add, state -> seen.add(state.revision()));
        attachment.accept(snapshot(9));
        assertTrue(page.isEmpty());
        attachment.accept(snapshot(12));
        attachment.accept(snapshot(11));
        page.remove().run();
        attachment.accept(snapshot(10));
        assertEquals(List.of(12L), seen);
        assertTrue(page.isEmpty());
    }

    @Test
    void updatesDuringSlowRenderDoNotQueueUntilItFinishesAndDoNotCatchUp() {
        Clock clock = new Clock();
        Queue<Runnable> page = new ArrayDeque<>();
        List<Long> seen = new ArrayList<>();
        AtomicReference<LatestSnapshotAttachment<Long>> holder = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean boundedDuringRender = new java.util.concurrent.atomic.AtomicBoolean();
        holder.set(new LatestSnapshotAttachment<>(snapshot(0), clock.policy(), page::add, state -> {
            seen.add(state.revision());
            if (state.revision() == 1) {
                for (int i = 2; i <= 100; i++) holder.get().accept(snapshot(i));
                clock.advance(200);
                boundedDuringRender.set(page.isEmpty() && clock.tasks.isEmpty());
            }
        }));
        holder.get().accept(snapshot(1));
        clock.advance(50);
        page.remove().run();
        assertEquals(1, page.size());
        page.remove().run();
        assertEquals(List.of(1L, 100L), seen);
        assertTrue(boundedDuringRender.get());
        assertTrue(page.isEmpty());
    }

    @Test
    void inlineExecutionAndRenderFailureDoNotStallOrRecurse() {
        AtomicReference<LatestSnapshotAttachment<Long>> holder = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        holder.set(new LatestSnapshotAttachment<>(snapshot(0), ActorRenderPolicy.immediate(),
                Runnable::run, state -> {
                    calls.incrementAndGet();
                    if (state.revision() < 10_000) holder.get().accept(snapshot(state.revision() + 1));
                    if (state.revision() == 2) throw new IllegalStateException("render failed");
                }));
        holder.get().accept(snapshot(1));
        assertEquals(10_000, calls.get());
    }

    @Test
    void closingCancelsTimerAndMakesAlreadyQueuedCallbacksHarmless() {
        Clock clock = new Clock();
        Queue<Runnable> page = new ArrayDeque<>();
        AtomicInteger calls = new AtomicInteger();
        var attachment = new LatestSnapshotAttachment<>(snapshot(0), clock.policy(), page::add,
                _ -> calls.incrementAndGet());
        attachment.accept(snapshot(1));
        attachment.close();
        assertTrue(clock.tasks.isEmpty());
        clock.advance(50);
        assertTrue(page.isEmpty());
        attachment.accept(snapshot(2));
        assertTrue(clock.tasks.isEmpty());

        var queued = new LatestSnapshotAttachment<>(snapshot(0), ActorRenderPolicy.immediate(),
                page::add, _ -> calls.incrementAndGet());
        queued.accept(snapshot(1));
        queued.close();
        page.remove().run();
        assertEquals(0, calls.get());
    }

    @Test
    void closingWhileSchedulerReturnsItsHandleStillCancelsIt() throws Exception {
        CountDownLatch scheduled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger cancelled = new AtomicInteger();
        AtomicInteger renders = new AtomicInteger();
        AtomicReference<Runnable> callback = new AtomicReference<>();
        var policy = new ActorRenderPolicy(Duration.ofMillis(50), (_, task) -> {
            callback.set(task);
            scheduled.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("timeout");
            } catch (InterruptedException e) { throw new AssertionError(e); }
            return cancelled::incrementAndGet;
        }, () -> 0);
        var attachment = new LatestSnapshotAttachment<>(snapshot(0), policy, Runnable::run,
                _ -> renders.incrementAndGet());
        Thread producer = Thread.ofVirtual().start(() -> attachment.accept(snapshot(1)));
        try {
            assertTrue(scheduled.await(5, TimeUnit.SECONDS));
            attachment.close();
            callback.get().run();
        } finally {
            release.countDown();
            producer.join(5000);
        }
        assertFalse(producer.isAlive());
        assertEquals(1, cancelled.get());
        assertEquals(0, renders.get());
    }

    @Test
    void terminalSnapshotUsesTheSameTrailingDeliveryAndRevisionRules() {
        Clock clock = new Clock();
        Queue<Runnable> page = new ArrayDeque<>();
        List<ActorSnapshot<Long>> seen = new ArrayList<>();
        var attachment = new LatestSnapshotAttachment<>(snapshot(0), clock.policy(), page::add, seen::add);
        attachment.accept(snapshot(1));
        attachment.accept(new ActorSnapshot<>(2, 1L, ActorSnapshot.Status.STOPPED));
        attachment.accept(snapshot(1));
        clock.advance(50);
        page.remove().run();
        assertEquals(1, seen.size());
        assertFalse(seen.getFirst().active());
    }

    @Test
    void stoppedSchedulerClosesAttachmentWithoutFailingThePageLoop() {
        AtomicInteger attempts = new AtomicInteger();
        var policy = new ActorRenderPolicy(Duration.ofMillis(50), (_, _) -> {
            attempts.incrementAndGet();
            throw new java.util.concurrent.RejectedExecutionException("stopped");
        }, () -> 0);
        AtomicInteger renders = new AtomicInteger();
        var attachment = new LatestSnapshotAttachment<>(snapshot(0), policy, Runnable::run,
                _ -> renders.incrementAndGet());
        attachment.accept(snapshot(1));
        attachment.accept(snapshot(2));
        assertEquals(1, attempts.get());
        assertEquals(0, renders.get());
    }

    private static ActorSnapshot<Long> snapshot(long revision) {
        return new ActorSnapshot<>(revision, revision, ActorSnapshot.Status.ACTIVE);
    }

    private static final class Clock implements ActorScheduler {
        private long nanos;
        private final List<Task> tasks = new ArrayList<>();
        ActorRenderPolicy policy() { return new ActorRenderPolicy(Duration.ofMillis(50), this, () -> nanos); }
        @Override public Cancellation schedule(Duration delay, Runnable task) {
            Task pending = new Task(nanos + delay.toNanos(), task);
            tasks.add(pending);
            return () -> tasks.remove(pending);
        }
        void advance(long millis) {
            nanos += Duration.ofMillis(millis).toNanos();
            for (Task task : List.copyOf(tasks)) {
                if (task.due <= nanos && tasks.remove(task)) task.run.run();
            }
        }
        private record Task(long due, Runnable run) { }
    }
}
