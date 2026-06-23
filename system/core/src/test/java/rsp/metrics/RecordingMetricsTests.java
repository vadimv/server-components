package rsp.metrics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RecordingMetricsTests {

    @Test
    void counter_starts_at_zero() {
        final RecordingMetrics m = new RecordingMetrics();
        assertEquals(0L, m.counter("never.touched"));
    }

    @Test
    void counter_increments_by_one() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a");
        m.incrementCounter("a");
        m.incrementCounter("a");
        assertEquals(3L, m.counter("a"));
    }

    @Test
    void counter_increments_by_delta() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a", 5);
        m.incrementCounter("a", 7);
        assertEquals(12L, m.counter("a"));
    }

    @Test
    void distinct_counters_are_independent() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a");
        m.incrementCounter("b", 10);
        assertEquals(1L, m.counter("a"));
        assertEquals(10L, m.counter("b"));
    }

    @Test
    void gauge_starts_at_zero() {
        final RecordingMetrics m = new RecordingMetrics();
        assertEquals(0L, m.gauge("never.set"));
    }

    @Test
    void gauge_overwrites() {
        final RecordingMetrics m = new RecordingMetrics();
        m.setGauge("g", 5);
        m.setGauge("g", 7);
        assertEquals(7L, m.gauge("g"));
    }

    @Test
    void counter_names_lists_only_touched() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a");
        m.incrementCounter("b");
        assertEquals(2, m.counterNames().size());
        assertTrue(m.counterNames().contains("a"));
        assertTrue(m.counterNames().contains("b"));
    }

    @Test
    void reset_clears_counters_and_gauges() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a", 3);
        m.setGauge("g", 5);
        m.reset();
        assertEquals(0L, m.counter("a"));
        assertEquals(0L, m.gauge("g"));
    }

    @Test
    void snapshot_returns_immutable_copy() {
        final RecordingMetrics m = new RecordingMetrics();
        m.incrementCounter("a", 3);
        final var snap = m.snapshotCounters();
        m.incrementCounter("a");
        assertEquals(3L, snap.get("a"), "snapshot must not see post-snapshot increments");
    }

    @Test
    void counter_is_thread_safe_under_contention() throws Exception {
        final RecordingMetrics m = new RecordingMetrics();
        final int threads = 8;
        final int incrementsPerThread = 10_000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        m.incrementCounter("contended");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals((long) threads * incrementsPerThread, m.counter("contended"));
    }
}
