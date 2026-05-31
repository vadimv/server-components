package rsp.app.posts.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LogStreamServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-27T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void emits_sequential_messages_and_keeps_a_rolling_buffer() {
        LogStreamService service = new LogStreamService(3, CLOCK, new Random(0L));

        service.emitNextEntry();
        service.emitNextEntry();
        service.emitNextEntry();
        List<LogEntry> snapshot = service.emitNextEntry();

        assertEquals(3, snapshot.size());
        assertEquals(generatedMessage(2), snapshot.get(0).message());
        assertEquals(generatedMessage(3), snapshot.get(1).message());
        assertEquals(generatedMessage(4), snapshot.get(2).message());
        assertEquals(2, snapshot.get(0).sequence());
        assertEquals(4, snapshot.get(2).sequence());
    }

    @Test
    void subscribers_receive_snapshots_until_unsubscribed() {
        LogStreamService service = new LogStreamService(5, CLOCK, new Random(1L));
        List<List<LogEntry>> received = new ArrayList<>();
        Runnable unsubscribe = service.subscribe(received::add);

        service.emitNextEntry();
        unsubscribe.run();
        service.emitNextEntry();

        assertEquals(1, received.size());
        assertEquals(generatedMessage(1), received.getFirst().getFirst().message());
    }

    @Test
    void start_seeds_an_initial_buffer_for_immediate_rendering() throws Exception {
        LogStreamService service = new LogStreamService(5, CLOCK, new Random(2L));

        service.start();
        try {
            List<LogEntry> snapshot = service.snapshot();
            assertEquals(5, snapshot.size());
            for (int i = 0; i < snapshot.size(); i++) {
                assertEquals(generatedMessage(i + 1), snapshot.get(i).message());
            }
        } finally {
            service.stop();
        }
    }

    @Test
    void same_seed_produces_identical_severity_sequence() {
        LogStreamService a = new LogStreamService(50, CLOCK, new Random(42L));
        LogStreamService b = new LogStreamService(50, CLOCK, new Random(42L));

        for (int i = 0; i < 200; i++) {
            LogEntry ae = a.emitNextEntry().getLast();
            LogEntry be = b.emitNextEntry().getLast();
            assertEquals(ae.level(), be.level(), "divergence at " + i);
            assertEquals(ae.message(), be.message());
        }
    }

    @Test
    void severity_distribution_is_roughly_85_12_3() {
        LogStreamService service = new LogStreamService(10, CLOCK, new Random(7L));
        EnumMap<LogEntry.Level, Integer> counts = new EnumMap<>(LogEntry.Level.class);
        for (LogEntry.Level level : LogEntry.Level.values()) {
            counts.put(level, 0);
        }
        int total = 5000;
        for (int i = 0; i < total; i++) {
            LogEntry entry = service.emitNextEntry().getLast();
            counts.merge(entry.level(), 1, Integer::sum);
        }
        double info = counts.get(LogEntry.Level.INFO) / (double) total;
        double warn = counts.get(LogEntry.Level.WARN) / (double) total;
        double error = counts.get(LogEntry.Level.ERROR) / (double) total;
        assertTrue(info > 0.80 && info < 0.90, "INFO ratio out of range: " + info);
        assertTrue(warn > 0.08 && warn < 0.16, "WARN ratio out of range: " + warn);
        assertTrue(error > 0.01 && error < 0.06, "ERROR ratio out of range: " + error);
    }

    @Test
    void snapshots_are_immutable() {
        LogStreamService service = new LogStreamService(3, CLOCK, new Random(0L));
        List<LogEntry> snapshot = service.emitNextEntry();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void rejects_invalid_buffer_size() {
        assertThrows(IllegalArgumentException.class,
                () -> new LogStreamService(0, CLOCK, new Random()));
    }

    private static String generatedMessage(final long sequence) {
        return "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " + sequence;
    }
}
