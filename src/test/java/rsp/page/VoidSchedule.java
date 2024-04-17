package rsp.page;

import rsp.ref.TimerRef;

import java.util.concurrent.TimeUnit;

public class VoidSchedule implements Schedule {
    public static Schedule INSTANCE = new VoidSchedule();

    @Override
    public void scheduleAtFixedRate(Runnable command, TimerRef key, long initialDelay, long period, TimeUnit unit) {}

    @Override
    public void schedule(Runnable command, TimerRef key, long delay, TimeUnit unit) {}

    @Override
    public void cancel(TimerRef key) {}
}
