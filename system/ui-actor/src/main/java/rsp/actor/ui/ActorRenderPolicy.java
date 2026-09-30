package rsp.actor.ui;

import rsp.actor.runtime.ActorScheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Latest-snapshot rendering cadence; the clock is monotonic and measured in nanoseconds. */
public record ActorRenderPolicy(Duration minimumInterval, ActorScheduler scheduler, LongSupplier nanoTime) {
    public ActorRenderPolicy {
        Objects.requireNonNull(minimumInterval, "minimumInterval");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(nanoTime, "nanoTime");
        if (minimumInterval.isNegative()) throw new IllegalArgumentException("Negative render interval");
        minimumInterval.toNanos();
    }

    public static ActorRenderPolicy throttled(Duration interval, ActorScheduler scheduler) {
        return new ActorRenderPolicy(interval, scheduler, System::nanoTime);
    }

    public static ActorRenderPolicy immediate() {
        return new ActorRenderPolicy(Duration.ZERO, (_, task) -> {
            task.run();
            return () -> { };
        }, System::nanoTime);
    }
}
