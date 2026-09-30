package rsp.actor.ui;

import rsp.actor.runtime.ActorScheduler;
import rsp.application.ApplicationLifecycle;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Application-owned timer shared by rendering attachments; work executes on the page loop. */
public final class ActorRenderScheduler implements ActorScheduler, ApplicationLifecycle {
    private final ScheduledThreadPoolExecutor executor =
            new ScheduledThreadPoolExecutor(1, Thread.ofVirtual().factory());

    public ActorRenderScheduler() {
        executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public Cancellation schedule(Duration delay, Runnable task) {
        var future = executor.schedule(task, delay.toNanos(), TimeUnit.NANOSECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }
}
