package rsp.app.posts.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommentRateStreamServiceTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-25T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void emits_deterministic_values_and_keeps_a_rolling_window() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120, 80), 2, CLOCK);

        service.emitNextSample();
        service.emitNextSample();
        List<CommentRateStreamService.Sample> snapshot = service.emitNextSample();

        assertEquals(2, snapshot.size());
        assertEquals(120, snapshot.get(0).value());
        assertEquals(80, snapshot.get(1).value());
        assertEquals(2, snapshot.get(0).sequence());
        assertEquals(3, snapshot.get(1).sequence());
        assertEquals(CLOCK.instant(), snapshot.get(1).timestamp());
    }

    @Test
    void subscribers_receive_snapshots_until_unsubscribed() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120), 5, CLOCK);
        List<List<CommentRateStreamService.Sample>> received = new ArrayList<>();
        Runnable unsubscribe = service.subscribe(received::add);

        service.emitNextSample();
        unsubscribe.run();
        service.emitNextSample();

        assertEquals(1, received.size());
        assertEquals(100, received.getFirst().getFirst().value());
    }

    @Test
    void start_seeds_an_initial_window_for_immediate_chart_rendering() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100, 120, 80), 5, CLOCK);

        service.start();
        List<CommentRateStreamService.Sample> snapshot = service.snapshot();
        service.stop();

        assertEquals(3, snapshot.size());
        assertEquals(List.of(100, 120, 80), snapshot.stream()
                .map(CommentRateStreamService.Sample::value)
                .toList());
        assertEquals(Instant.parse("2026-05-25T10:15:28Z"), snapshot.get(0).timestamp());
        assertEquals(Instant.parse("2026-05-25T10:15:30Z"), snapshot.get(2).timestamp());
    }

    @Test
    void snapshots_are_immutable() {
        CommentRateStreamService service = new CommentRateStreamService(List.of(100), 5, CLOCK);
        List<CommentRateStreamService.Sample> snapshot = service.emitNextSample();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void rejects_empty_demo_values_and_invalid_window_size() {
        assertThrows(IllegalArgumentException.class,
                () -> new CommentRateStreamService(List.of(), 5, CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> new CommentRateStreamService(List.of(100), 0, CLOCK));
    }
}
