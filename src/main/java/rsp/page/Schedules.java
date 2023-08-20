package rsp.page;

import rsp.ref.TimerRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Schedules implements Schedule {
    private final Map<TimerRef, ScheduledFuture<?>> schedules = new HashMap<>();
    private final ScheduledExecutorService scheduledExecutorService;

    public Schedules(final ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService);
    }

    @Override
    public void scheduleAtFixedRate(final Runnable command, final TimerRef key, final long initialDelay, final long period, final TimeUnit unit) {
        final ScheduledFuture<?> timer = scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
        schedules.put(key, timer);
    }

    @Override
    public void schedule(final Runnable command, final TimerRef key, final long delay, final TimeUnit unit) {
        final ScheduledFuture<?> timer = scheduledExecutorService.schedule(command, delay, unit);
        schedules.put(key, timer);
    }

    @Override
    public void cancel(final TimerRef key) {
        final ScheduledFuture<?> schedule = schedules.get(key);
        if (schedule != null) {
            schedule.cancel(true);
            schedules.remove(key);
        }
    }

    public void cancelAll() {
        for (final var timer : schedules.entrySet()) {
            timer.getValue().cancel(true);
        }
        schedules.clear();
    }

}
