package rsp.metrics;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe in-process metric registry backed only by JDK classes.
 *
 * <p>Only names and update types declared in its {@link MetricCatalog} are accepted.
 * Invalid updates are ignored and counted when the catalog contains
 * {@link MetricNames#METRIC_UPDATES_REJECTED}.</p>
 */
public final class MetricRegistry implements Metrics {
    private final MetricCatalog catalog;
    private final Clock clock;
    private final Instant startedAt;
    private final Map<String, LongAdder> counters;
    private final Map<String, AtomicLong> gauges;

    /** Creates a registry using the system UTC clock. */
    public MetricRegistry(final MetricCatalog catalog) {
        this(catalog, Clock.systemUTC());
    }

    MetricRegistry(final MetricCatalog catalog, final Clock clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.startedAt = clock.instant();

        final Map<String, LongAdder> newCounters = new LinkedHashMap<>();
        final Map<String, AtomicLong> newGauges = new LinkedHashMap<>();
        for (final MetricDescriptor descriptor : catalog.descriptors()) {
            if (descriptor.kind() == MetricKind.COUNTER) {
                newCounters.put(descriptor.name(), new LongAdder());
            } else {
                newGauges.put(descriptor.name(), new AtomicLong());
            }
        }
        counters = Map.copyOf(newCounters);
        gauges = Map.copyOf(newGauges);
    }

    @Override
    public void incrementCounter(final String name) {
        incrementCounter(name, 1);
    }

    @Override
    public void incrementCounter(final String name, final long delta) {
        if (name == null) {
            rejectUpdate();
            return;
        }
        final LongAdder counter = counters.get(name);
        if (counter == null || delta < 0) {
            rejectUpdate();
            return;
        }
        counter.add(delta);
    }

    @Override
    public void setGauge(final String name, final long value) {
        if (name == null) {
            rejectUpdate();
            return;
        }
        final AtomicLong gauge = gauges.get(name);
        if (gauge == null) {
            rejectUpdate();
            return;
        }
        gauge.set(value);
    }

    /** Returns the catalog used to validate updates and describe exported values. */
    public MetricCatalog catalog() {
        return catalog;
    }

    /** Returns one current value, failing if the name is not in the catalog. */
    public long value(final String name) {
        final LongAdder counter = counters.get(name);
        if (counter != null) {
            return counter.sum();
        }
        final AtomicLong gauge = gauges.get(name);
        if (gauge != null) {
            return gauge.get();
        }
        throw new IllegalArgumentException("Unknown metric name");
    }

    /** Copies all current values in stable catalog order. */
    public MetricSnapshot snapshot() {
        final Map<String, Long> values = new LinkedHashMap<>();
        for (final MetricDescriptor descriptor : catalog.descriptors()) {
            values.put(descriptor.name(), value(descriptor.name()));
        }
        return new MetricSnapshot(startedAt, clock.instant(), values);
    }

    private void rejectUpdate() {
        final LongAdder rejected = counters.get(MetricNames.METRIC_UPDATES_REJECTED);
        if (rejected != null) {
            rejected.increment();
        }
    }
}
