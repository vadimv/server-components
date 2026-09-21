package rsp.actor.runtime;

import java.time.Duration;

/** One-shot scheduling SPI; tests can supply a manually advanced clock. */
@FunctionalInterface
public interface ActorScheduler {
    Cancellation schedule(Duration delay, Runnable task);

    @FunctionalInterface
    interface Cancellation {
        void cancel();
    }
}
