package rsp.metrics;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricObjectRegistryTests {
    private static final MetricDescriptor VALUE = MetricDescriptor.gauge(
            "test.counter.value", "1", "Current counter value", "CurrentValue");
    private static final MetricDescriptor ACTIONS = MetricDescriptor.counter(
            "test.counter.actions", "1", "Actions applied", "Actions");
    private static final MetricObjectType COUNTER_TYPE = new MetricObjectType(
            "test.counter", "Counter", MetricCatalog.of(VALUE, ACTIONS));
    private static final MetricObjectCatalog OBJECT_CATALOG = MetricObjectCatalog.of(COUNTER_TYPE);

    @Test
    void creates_independent_objects_with_opaque_monotonic_ordinals() {
        final Instant now = Instant.parse("2026-09-13T12:00:00Z");
        final MetricRegistry registry = registry(10, Clock.fixed(now, ZoneOffset.UTC));

        final MetricObject first = registry.openObject(COUNTER_TYPE);
        final MetricObject second = registry.openObject(COUNTER_TYPE);
        first.setGauge(VALUE.name(), 7);
        first.incrementCounter(ACTIONS.name(), 2);
        second.setGauge(VALUE.name(), 11);

        assertEquals(0, first.instanceNumber());
        assertEquals(1, second.instanceNumber());
        assertNotEquals(first.instanceNumber(), second.instanceNumber());
        assertEquals(7, first.value(VALUE.name()));
        assertEquals(2, first.value(ACTIONS.name()));
        assertEquals(11, second.value(VALUE.name()));
        assertEquals(0, second.value(ACTIONS.name()));
        assertEquals(List.of(0L, 1L), registry.metricObjectSnapshots().stream()
                .map(MetricObjectSnapshot::instanceNumber)
                .toList());
        assertEquals(now, first.snapshot().startedAt());
        assertEquals(2, registry.value(MetricNames.METRIC_OBJECTS_CREATED));
        assertEquals(2, registry.value(MetricNames.METRIC_OBJECTS_ACTIVE));
    }

    @Test
    void allocates_ordinals_independently_for_each_object_type() {
        final MetricDescriptor connections = MetricDescriptor.counter(
                "test.connection.messages", "1", "Messages", "Messages");
        final MetricObjectType connectionType = new MetricObjectType(
                "test.connection", "Connection", MetricCatalog.of(connections));
        final MetricRegistry registry = new MetricRegistry(
                MetricNames.frameworkCatalog(),
                MetricObjectCatalog.of(COUNTER_TYPE, connectionType),
                10);

        final MetricObject firstCounter = registry.openObject(COUNTER_TYPE);
        final MetricObject firstConnection = registry.openObject(connectionType);
        final MetricObject secondCounter = registry.openObject(COUNTER_TYPE);
        final MetricObject secondConnection = registry.openObject(connectionType);

        assertEquals(0, firstCounter.instanceNumber());
        assertEquals(0, firstConnection.instanceNumber());
        assertEquals(1, secondCounter.instanceNumber());
        assertEquals(1, secondConnection.instanceNumber());
        assertEquals(List.of(COUNTER_TYPE, connectionType, COUNTER_TYPE, connectionType),
                     registry.metricObjectSnapshots().stream()
                             .map(MetricObjectSnapshot::type)
                             .toList());

        firstCounter.close();
        assertEquals(3, registry.activeMetricObjectCount());
        assertFalse(firstConnection.isClosed());
    }

    @Test
    void close_is_idempotent_and_rejects_updates_after_close() {
        final MetricRegistry registry = registry(10);
        final MetricObject object = registry.openObject(COUNTER_TYPE);

        object.close();
        object.close();
        object.setGauge(VALUE.name(), 5);
        object.incrementCounter(ACTIONS.name());

        assertTrue(object.isClosed());
        assertEquals(0, registry.activeMetricObjectCount());
        assertEquals(0, registry.value(MetricNames.METRIC_OBJECTS_ACTIVE));
        assertEquals(2, registry.value(MetricNames.METRIC_UPDATES_REJECTED));
    }

    @Test
    void rejects_unknown_mismatched_over_capacity_and_post_shutdown_opens() {
        final MetricRegistry registry = registry(1);
        final MetricObjectType unknown = new MetricObjectType(
                "test.unknown", "Unknown", MetricCatalog.of(
                        MetricDescriptor.gauge("test.unknown.value", "1", "Unknown", "Value")));
        final MetricObjectType mismatched = new MetricObjectType(
                COUNTER_TYPE.name(), COUNTER_TYPE.jmxType(), MetricCatalog.of(
                        MetricDescriptor.gauge("test.counter.other", "1", "Other", "Other")));

        final MetricObject accepted = registry.openObject(COUNTER_TYPE);
        final MetricObject overCapacity = registry.openObject(COUNTER_TYPE);
        final MetricObject unknownObject = registry.openObject(unknown);
        final MetricObject mismatchedObject = registry.openObject(mismatched);
        registry.closeMetricObjects();
        final MetricObject afterShutdown = registry.openObject(COUNTER_TYPE);

        assertEquals(0, accepted.instanceNumber());
        assertTrue(overCapacity.isClosed());
        assertTrue(unknownObject.isClosed());
        assertTrue(mismatchedObject.isClosed());
        assertTrue(afterShutdown.isClosed());
        assertEquals(-1, afterShutdown.instanceNumber());
        assertEquals(4, registry.value(MetricNames.METRIC_OBJECTS_REJECTED));
        assertEquals(0, registry.activeMetricObjectCount());
    }

    @Test
    void notifies_adapters_without_allowing_adapter_failures_to_escape() {
        final MetricRegistry registry = registry(10);
        final List<String> events = new ArrayList<>();
        final MetricObjectListener.Registration recording = registry.registerMetricObjectListener(
                new MetricObjectListener() {
                    @Override
                    public void onOpened(final MetricObject object) {
                        events.add("open:" + object.instanceNumber());
                    }

                    @Override
                    public void onClosed(final MetricObject object) {
                        events.add("close:" + object.instanceNumber());
                    }
                });
        registry.registerMetricObjectListener(new MetricObjectListener() {
            @Override
            public void onOpened(final MetricObject object) {
                throw new IllegalStateException("adapter failed");
            }

            @Override
            public void onClosed(final MetricObject object) {
                throw new IllegalStateException("adapter failed");
            }
        });

        final MetricObject first = registry.openObject(COUNTER_TYPE);
        first.close();
        recording.close();
        final MetricObject second = registry.openObject(COUNTER_TYPE);
        second.close();

        assertEquals(List.of("open:0", "close:0"), events);
        assertEquals(4, registry.value(MetricNames.METRIC_OBJECT_ADAPTER_FAILURES));
    }

    @Test
    void shutdown_closes_all_objects_and_prevents_future_opens() {
        final MetricRegistry registry = registry(10);
        final MetricObject first = registry.openObject(COUNTER_TYPE);
        final MetricObject second = registry.openObject(COUNTER_TYPE);

        registry.closeMetricObjects();
        registry.closeMetricObjects();

        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
        assertEquals(0, registry.activeMetricObjectCount());
        assertTrue(registry.openObject(COUNTER_TYPE).isClosed());
    }

    @Test
    void concurrent_opens_allocate_unique_instance_numbers() throws Exception {
        final int workers = 16;
        final MetricRegistry registry = registry(workers);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(workers);

        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        registry.openObject(COUNTER_TYPE);
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

        assertEquals(workers, registry.activeMetricObjectCount());
        assertEquals(workers, registry.metricObjectSnapshots().stream()
                .map(MetricObjectSnapshot::instanceNumber)
                .distinct()
                .count());
    }

    @Test
    void object_catalog_rejects_ambiguous_or_unsafe_schemas() {
        final MetricObjectType otherTypeWithSameMetric = new MetricObjectType(
                "test.other", "Other", MetricCatalog.of(VALUE));
        final MetricObjectType otherTypeWithSameJmxType = new MetricObjectType(
                "test.other", COUNTER_TYPE.jmxType(), MetricCatalog.of(
                        MetricDescriptor.gauge("test.other.value", "1", "Other", "Other")));

        assertThrows(IllegalArgumentException.class,
                     () -> MetricObjectCatalog.of(COUNTER_TYPE, otherTypeWithSameMetric));
        assertThrows(IllegalArgumentException.class,
                     () -> MetricObjectCatalog.of(COUNTER_TYPE, otherTypeWithSameJmxType));
        assertThrows(IllegalArgumentException.class,
                     () -> new MetricObjectType("unsafe", "Safe", MetricCatalog.of(VALUE)));
        assertThrows(IllegalArgumentException.class,
                     () -> new MetricObjectType("test.safe", "Unsafe:Value", MetricCatalog.of(VALUE)));
        assertThrows(IllegalArgumentException.class,
                     () -> new MetricRegistry(MetricCatalog.of(VALUE), OBJECT_CATALOG));
    }

    @Test
    void object_catalog_can_append_non_conflicting_application_types() {
        final MetricObjectType other = new MetricObjectType(
                "test.other", "Other", MetricCatalog.of(
                        MetricDescriptor.gauge("test.other.value", "1", "Other", "Other")));

        final MetricObjectCatalog extended = OBJECT_CATALOG.with(other);

        assertEquals(List.of(COUNTER_TYPE, other), extended.types());
        assertEquals(other, extended.type("test.other").orElseThrow());
    }

    private static MetricRegistry registry(final int maxActiveObjects) {
        return registry(maxActiveObjects, Clock.systemUTC());
    }

    private static MetricRegistry registry(final int maxActiveObjects, final Clock clock) {
        return new MetricRegistry(
                MetricNames.frameworkCatalog(), OBJECT_CATALOG, maxActiveObjects, clock);
    }
}
