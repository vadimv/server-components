package rsp.actor.testkit;

import rsp.actor.runtime.ActorScheduler;

import java.time.Duration;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Objects;

/** Virtual monotonic clock for delayed messages and ask timeouts. */
public final class ManualActorScheduler implements ActorScheduler {
    private final PriorityQueue<Task> tasks = new PriorityQueue<>(
            Comparator.comparingLong((Task task) -> task.dueNanos)
                    .thenComparingLong(task -> task.sequence));
    private long nowNanos;
    private long sequence;

    @Override
    public Cancellation schedule(Duration delay, Runnable task) {
        if (Objects.requireNonNull(delay, "delay").isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        Task scheduled = new Task(Math.addExact(nowNanos, delay.toNanos()),
                sequence++, Objects.requireNonNull(task, "task"));
        tasks.add(scheduled);
        return () -> {
            scheduled.cancelled = true;
            tasks.remove(scheduled);
        };
    }

    public Duration now() {
        return Duration.ofNanos(nowNanos);
    }

    public int pendingCount() {
        return (int) tasks.stream().filter(task -> !task.cancelled).count();
    }

    public void advance(Duration duration) {
        if (Objects.requireNonNull(duration, "duration").isNegative()) {
            throw new IllegalArgumentException("advance must not be negative");
        }
        long target = Math.addExact(nowNanos, duration.toNanos());
        int steps = 0;
        while (!tasks.isEmpty() && tasks.peek().dueNanos <= target) {
            Task next = tasks.remove();
            nowNanos = next.dueNanos;
            if (!next.cancelled) {
                next.task.run();
            }
            if (++steps > 100_000) {
                throw new IllegalStateException("Manual actor scheduler exceeded 100,000 steps");
            }
        }
        nowNanos = target;
    }

    private static final class Task {
        private final long dueNanos;
        private final long sequence;
        private final Runnable task;
        private boolean cancelled;

        private Task(long dueNanos, long sequence, Runnable task) {
            this.dueNanos = dueNanos;
            this.sequence = sequence;
            this.task = task;
        }
    }
}
