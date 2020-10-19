package rsp;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface Schedule {

    ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                           long initialDelay,
                                           long period,
                                           TimeUnit unit);

    ScheduledFuture<?> schedule(Runnable command,
                                long delay, TimeUnit unit);
}
