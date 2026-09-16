package rsp.metrics;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** A point-in-time, transport-neutral copy of all values in a metric registry. */
public record MetricSnapshot(Instant startedAt,
                             Instant observedAt,
                             Map<String, Long> values) {
    /** Creates an immutable snapshot. */
    public MetricSnapshot {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(values, "values");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** Returns a known metric value or fails for a name outside the snapshot catalog. */
    public long value(final String name) {
        final Long value = values.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Unknown metric name");
        }
        return value;
    }
}
