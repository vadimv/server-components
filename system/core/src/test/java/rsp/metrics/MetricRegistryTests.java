package rsp.metrics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricRegistryTests {
    private static final MetricCatalog CATALOG = MetricCatalog.of(
            MetricDescriptor.counter("test.requests", "1", "Requests", "Requests"),
            MetricDescriptor.gauge("test.active", "1", "Active work", "Active"),
            MetricDescriptor.counter(MetricNames.METRIC_UPDATES_REJECTED,
                                     "1",
                                     "Rejected updates",
                                     "MetricUpdatesRejected"));

    @Test
    void records_catalogued_counters_and_gauges() {
        final MetricRegistry registry = new MetricRegistry(CATALOG);

        registry.incrementCounter("test.requests");
        registry.incrementCounter("test.requests", 4);
        registry.setGauge("test.active", 3);
        registry.setGauge("test.active", 2);

        assertEquals(5, registry.value("test.requests"));
        assertEquals(2, registry.value("test.active"));
        assertEquals(0, registry.value(MetricNames.METRIC_UPDATES_REJECTED));
    }

    @Test
    void rejects_unknown_names_type_mismatches_and_negative_counters() {
        final MetricRegistry registry = new MetricRegistry(CATALOG);

        registry.incrementCounter("test.unknown");
        registry.incrementCounter("test.active");
        registry.incrementCounter("test.requests", -1);
        registry.incrementCounter(null);
        registry.setGauge("test.requests", 7);
        registry.setGauge("test.unknown", 7);
        registry.setGauge(null, 7);

        assertEquals(0, registry.value("test.requests"));
        assertEquals(0, registry.value("test.active"));
        assertEquals(7, registry.value(MetricNames.METRIC_UPDATES_REJECTED));
    }

    @Test
    void snapshot_includes_zero_values_and_registry_timestamps() {
        final Instant now = Instant.parse("2026-09-13T12:00:00Z");
        final MetricRegistry registry = new MetricRegistry(CATALOG, Clock.fixed(now, ZoneOffset.UTC));

        final MetricSnapshot snapshot = registry.snapshot();

        assertEquals(now, snapshot.startedAt());
        assertEquals(now, snapshot.observedAt());
        assertEquals(0, snapshot.value("test.requests"));
        assertEquals(CATALOG.descriptors().size(), snapshot.values().size());
        assertThrows(UnsupportedOperationException.class,
                     () -> snapshot.values().put("test.requests", 1L));
    }

    @Test
    void counter_updates_are_thread_safe() throws Exception {
        final MetricRegistry registry = new MetricRegistry(CATALOG);
        final int workers = 8;
        final int incrementsPerWorker = 5_000;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(workers);

        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int increment = 0; increment < incrementsPerWorker; increment++) {
                            registry.incrementCounter("test.requests");
                        }
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        }

        assertEquals((long) workers * incrementsPerWorker, registry.value("test.requests"));
    }

    @Test
    void catalog_rejects_duplicate_names_and_jmx_attributes() {
        final MetricDescriptor first = MetricDescriptor.counter("test.first", "1", "First", "Shared");

        assertThrows(IllegalArgumentException.class,
                     () -> MetricCatalog.of(first,
                                            MetricDescriptor.gauge("test.first", "1", "Duplicate", "Other")));
        assertThrows(IllegalArgumentException.class,
                     () -> MetricCatalog.of(first,
                                            MetricDescriptor.gauge("test.second", "1", "Duplicate", "Shared")));
    }

    @Test
    void framework_catalog_contains_only_stable_metadata() {
        final MetricCatalog catalog = MetricNames.frameworkCatalog().with(
                MetricDescriptor.counter("app.jobs.completed", "1", "Completed jobs", "AppJobsCompleted"));

        assertTrue(catalog.descriptor(MetricNames.HTTP_REQUESTS).isPresent());
        assertTrue(catalog.descriptor(MetricNames.WEB_SOCKET_CONNECTIONS_ACTIVE).isPresent());
        assertTrue(catalog.descriptor(MetricNames.PAGE_SESSIONS_ACTIVE).isPresent());
        assertTrue(catalog.descriptor("app.jobs.completed").isPresent());
    }
}
