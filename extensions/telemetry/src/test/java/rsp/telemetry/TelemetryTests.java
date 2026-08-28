package rsp.telemetry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryTests {
    private static final TelemetryKey<Double> TEMPERATURE =
            TelemetryKey.of("plant.temperature", Double.class, "°C");

    @Test
    void resolves_typed_scalar_and_series_sources() {
        Telemetry<Double> scalar = new Telemetry<>() {
            @Override
            public Optional<TelemetrySample<Double>> snapshot() {
                return Optional.empty();
            }

            @Override
            public Subscription subscribe(java.util.function.Consumer<TelemetrySample<Double>> subscriber) {
                return Subscription.none();
            }
        };
        TelemetrySeries<Double> series = new TelemetrySeries<>() {
            @Override
            public List<TelemetrySample<Double>> snapshot() {
                return List.of();
            }

            @Override
            public Subscription subscribe(java.util.function.Consumer<List<TelemetrySample<Double>>> subscriber) {
                return Subscription.none();
            }
        };

        TelemetryRegistry registry = MapTelemetryRegistry.builder()
                .bind(TEMPERATURE, scalar)
                .bindSeries(TEMPERATURE, series)
                .build();

        assertSame(scalar, registry.resolve(TEMPERATURE));
        assertSame(series, registry.resolveSeries(TEMPERATURE));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(TelemetryKey.of("missing", Double.class)));
    }

    @Test
    void freshness_uses_the_supplied_clock() {
        Instant timestamp = Instant.parse("2026-08-27T10:00:00Z");
        TelemetrySample<Double> sample = TelemetrySample.good(21.5, timestamp);

        assertFalse(sample.isStale(Duration.ofSeconds(30),
                Clock.fixed(timestamp.plusSeconds(30), ZoneOffset.UTC)));
        assertTrue(sample.isStale(Duration.ofSeconds(30),
                Clock.fixed(timestamp.plusSeconds(31), ZoneOffset.UTC)));
    }

    @Test
    void subscription_close_action_runs_once() {
        AtomicInteger closes = new AtomicInteger();
        Subscription subscription = Subscription.once(closes::incrementAndGet);

        subscription.close();
        subscription.close();

        assertEquals(1, closes.get());
    }
}
