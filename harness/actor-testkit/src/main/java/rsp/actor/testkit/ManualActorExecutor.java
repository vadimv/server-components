package rsp.actor.testkit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Deterministic queue of runtime work. No background thread or sleeps. */
public final class ManualActorExecutor implements Executor {
    private final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
        tasks.addLast(Objects.requireNonNull(task, "task"));
    }

    public int pendingCount() {
        return tasks.size();
    }

    public boolean runNext() {
        Runnable next = tasks.pollFirst();
        if (next == null) {
            return false;
        }
        next.run();
        return true;
    }

    public void runAll() {
        int steps = 0;
        while (runNext()) {
            if (++steps > 100_000) {
                throw new IllegalStateException("Manual actor executor exceeded 100,000 steps");
            }
        }
    }
}
