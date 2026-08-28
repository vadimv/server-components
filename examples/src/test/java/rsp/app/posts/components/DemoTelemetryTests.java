package rsp.app.posts.components;

import org.junit.jupiter.api.Test;
import rsp.app.posts.services.CommentRateStreamService;
import rsp.app.posts.services.LogEntry;
import rsp.app.posts.services.LogStreamService;
import rsp.telemetry.TelemetryRegistry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoTelemetryTests {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-25T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void adapts_existing_stream_services_to_typed_telemetry() {
        CommentRateStreamService comments =
                new CommentRateStreamService(List.of(100, 120), 5, CLOCK);
        comments.emitNextSample();
        comments.emitNextSample();
        LogStreamService logs = new LogStreamService(5, CLOCK, new Random(0L));
        logs.emitNextEntry();

        TelemetryRegistry telemetry = DemoTelemetry.registry(comments, logs);

        assertEquals(120.0,
                telemetry.resolveSeries(DemoTelemetry.COMMENTS_RATE).snapshot().getLast().value());
        LogEntry entry = telemetry.resolveSeries(DemoTelemetry.LOG_ENTRIES).snapshot().getLast().value();
        assertEquals(1, entry.sequence());
        assertEquals(CLOCK.instant(), entry.timestamp());
    }
}
