package rsp.page;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Schedule {

    ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                           Object key,
                                           long initialDelay,
                                           long period,
                                           TimeUnit unit);

    ScheduledFuture<?> schedule(Runnable command,
                                Object key,
                                long delay, TimeUnit unit);

    void cancel(Object key);
}
