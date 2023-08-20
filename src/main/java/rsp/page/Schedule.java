package rsp.page;

import rsp.ref.TimerRef;

import java.util.concurrent.TimeUnit;

public interface Schedule {

    void scheduleAtFixedRate(Runnable command,
                             TimerRef key,
                             long initialDelay,
                             long period,
                             TimeUnit unit);

    void schedule(Runnable command,
                  TimerRef key,
                  long delay,
                  TimeUnit unit);

    void cancel(TimerRef key);
}
