package rsp.metrics;

import java.util.Objects;

/** Immutable transport-neutral snapshot of one live metric object and its per-type ordinal. */
public record MetricObjectSnapshot(MetricObjectType type,
                                   long instanceNumber,
                                   MetricSnapshot metrics) {
    /** Validates a metric-object snapshot. */
    public MetricObjectSnapshot {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(metrics, "metrics");
        if (instanceNumber < 0) {
            throw new IllegalArgumentException("Metric-object instance number must be non-negative");
        }
    }
}
