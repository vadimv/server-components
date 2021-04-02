package rsp.page;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface Schedule<S> {

    Timer scheduleAtFixedRate(Consumer<S> command,
                              Object key,
                              long initialDelay,
                              long period,
                              TimeUnit unit);

    Timer schedule(Consumer<S> command,
                               Object key,
                               long delay, TimeUnit unit);

    void cancel(Object key);
}
