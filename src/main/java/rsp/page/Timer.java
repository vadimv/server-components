package rsp.page;

import java.util.concurrent.ScheduledFuture;

public class Timer {

    public final Object key;

    private final ScheduledFuture<?> schedule;
    private final Runnable cancellation;

    public Timer(Object key, ScheduledFuture<?> schedule, Runnable cancellation) {
        this.key = key;
        this.schedule = schedule;
        this.cancellation = cancellation;
    }

    public void cancel() {
        cancellation.run();
    }
}
