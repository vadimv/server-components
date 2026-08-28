package rsp.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** An immutable telemetry value with source time and quality. */
public record TelemetrySample<T>(T value, Instant timestamp, TelemetryQuality quality) {

    public TelemetrySample {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(quality, "quality");
    }

    public static <T> TelemetrySample<T> good(T value, Instant timestamp) {
        return new TelemetrySample<>(value, timestamp, TelemetryQuality.GOOD);
    }

    /** Evaluates freshness deterministically with the caller's clock. */
    public boolean isStale(Duration staleAfter, Clock clock) {
        Objects.requireNonNull(staleAfter, "staleAfter");
        Objects.requireNonNull(clock, "clock");
        if (staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative");
        }
        return timestamp.plus(staleAfter).isBefore(clock.instant());
    }
}
