package rsp.metrics;

/** The update semantics of a metric. */
public enum MetricKind {
    /** A monotonically increasing total. */
    COUNTER,

    /** An absolute value that may increase or decrease. */
    GAUGE
}
