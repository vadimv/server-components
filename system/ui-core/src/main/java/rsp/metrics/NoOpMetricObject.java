package rsp.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Disabled metric-object handle used when no registry accepts an object. */
final class NoOpMetricObject implements MetricObject {
    private final MetricObjectType type;

    NoOpMetricObject(final MetricObjectType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public MetricObjectType type() {
        return type;
    }

    @Override
    public long instanceNumber() {
        return -1;
    }

    @Override
    public boolean isClosed() {
        return true;
    }

    @Override
    public void incrementCounter(final String name) {
    }

    @Override
    public void incrementCounter(final String name, final long delta) {
    }

    @Override
    public void setGauge(final String name, final long value) {
    }

    @Override
    public long value(final String name) {
        if (type.metrics().descriptor(name).isEmpty()) {
            throw new IllegalArgumentException("Unknown metric name");
        }
        return 0;
    }

    @Override
    public MetricSnapshot snapshot() {
        final Map<String, Long> values = new LinkedHashMap<>();
        for (final MetricDescriptor descriptor : type.metrics().descriptors()) {
            values.put(descriptor.name(), 0L);
        }
        return new MetricSnapshot(Instant.EPOCH, Instant.EPOCH, values);
    }

    @Override
    public void close() {
    }
}
