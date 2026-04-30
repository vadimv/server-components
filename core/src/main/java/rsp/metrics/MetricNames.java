package rsp.metrics;

/**
 * Centralised metric name constants. Each name is a public stability contract:
 * tests assert against them and production dashboards/alerts may depend on them.
 * <p>
 * Naming convention: {@code rsp.<area>.<event>}. Areas use snake_case for
 * compound words. Names must have bounded cardinality — never include session
 * ids, component ids, or other unbounded values.
 * <p>
 * Each constant ships with a one-line justification of why the metric earns
 * its place. Add new constants only with a clear test or operational use case.
 */
public final class MetricNames {

    private MetricNames() {}

    // ===== Component segment lifecycle =====

    /** A new {@code ComponentSegment} instance was constructed. */
    public static final String SEGMENT_CREATED = "rsp.segment.created";

    /** {@code ComponentSegment.unmount()} ran to completion (idempotent: re-entry is not counted). */
    public static final String SEGMENT_UNMOUNTED = "rsp.segment.unmounted";

    /**
     * A state update was rejected because the segment was already unmounted.
     * This is the ghost-component canary — non-zero in production indicates
     * the framework's reconciliation invariant is being violated somewhere.
     */
    public static final String SEGMENT_UPDATE_DROPPED_UNMOUNTED = "rsp.segment.update.dropped_unmounted";
}
