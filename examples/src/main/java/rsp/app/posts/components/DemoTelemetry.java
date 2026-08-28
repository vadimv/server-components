package rsp.app.posts.components;

import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogEntry;
import rsp.app.posts.services.LogStreamService;
import rsp.telemetry.MapTelemetryRegistry;
import rsp.telemetry.Subscription;
import rsp.telemetry.TelemetryKey;
import rsp.telemetry.TelemetryRegistry;
import rsp.telemetry.TelemetrySample;
import rsp.telemetry.TelemetrySeries;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Protocol adapters that keep the example's producers outside the dashboard DSL. */
public final class DemoTelemetry {
    public static final TelemetryKey<Double> COMMENTS_RATE =
            TelemetryKey.of("comments.rate", Double.class, "comments/sec");
    public static final TelemetryKey<LogEntry> LOG_ENTRIES =
            TelemetryKey.of("application.logs", LogEntry.class);

    private DemoTelemetry() {}

    public static TelemetryRegistry registry(CommentRateStreamService comments,
                                              LogStreamService logs) {
        return MapTelemetryRegistry.builder()
                .bindSeries(COMMENTS_RATE, commentsRate(comments))
                .bindSeries(LOG_ENTRIES, logEntries(logs))
                .build();
    }

    private static TelemetrySeries<Double> commentsRate(CommentRateStreamService service) {
        Objects.requireNonNull(service, "service");
        return new TelemetrySeries<>() {
            @Override
            public List<TelemetrySample<Double>> snapshot() {
                return commentSamples(service.snapshot());
            }

            @Override
            public Subscription subscribe(Consumer<List<TelemetrySample<Double>>> subscriber) {
                Runnable unsubscribe = service.subscribe(samples -> subscriber.accept(commentSamples(samples)));
                return Subscription.once(unsubscribe);
            }
        };
    }

    private static TelemetrySeries<LogEntry> logEntries(LogStreamService service) {
        Objects.requireNonNull(service, "service");
        return new TelemetrySeries<>() {
            @Override
            public List<TelemetrySample<LogEntry>> snapshot() {
                return logSamples(service.snapshot());
            }

            @Override
            public Subscription subscribe(Consumer<List<TelemetrySample<LogEntry>>> subscriber) {
                Runnable unsubscribe = service.subscribe(entries -> subscriber.accept(logSamples(entries)));
                return Subscription.once(unsubscribe);
            }
        };
    }

    private static List<TelemetrySample<Double>> commentSamples(
            List<CommentRateStreamService.Sample> samples) {
        return samples.stream()
                .map(sample -> TelemetrySample.good((double) sample.value(), sample.timestamp()))
                .toList();
    }

    private static List<TelemetrySample<LogEntry>> logSamples(List<LogEntry> entries) {
        return entries.stream()
                .map(entry -> TelemetrySample.good(entry, entry.timestamp()))
                .toList();
    }
}
