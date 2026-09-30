package rsp.actor.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/** Runs reentrant tasks iteratively while retaining the delegate executor's scheduling. */
public final class TrampolineExecutor implements Executor {
    private final Executor delegate;
    private final ThreadLocal<Deque<Runnable>> running = new ThreadLocal<>();

    public TrampolineExecutor(Executor delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(Runnable task) {
        // Always submit to the delegate so queued executors retain their scheduling
        // and rejection behavior. Only reentrant execution on this thread is deferred.
        delegate.execute(() -> run(task));
    }

    private void run(Runnable task) {
        Deque<Runnable> queued = running.get();
        if (queued != null) {
            queued.addLast(task);
            return;
        }
        queued = new ArrayDeque<>();
        running.set(queued);
        try {
            // Activations contain task failures so one actor cannot abort this drain.
            Runnable next = task;
            do {
                next.run();
                next = queued.pollFirst();
            } while (next != null);
        } finally {
            running.remove();
        }
    }
}
