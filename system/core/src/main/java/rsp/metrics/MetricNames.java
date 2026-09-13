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

    // ===== HTTP and live-session lifecycle =====

    /** A syntactically valid HTTP request was received, including WebSocket upgrade requests. */
    public static final String HTTP_REQUESTS = "rsp.http.requests";

    /** A request could not complete normally due to protocol, rendering, handshake, or I/O failure. */
    public static final String HTTP_FAILURES = "rsp.http.failures";

    /** WebSocket connections currently registered in this process. */
    public static final String WEB_SOCKET_CONNECTIONS_ACTIVE = "rsp.websocket.connections.active";

    /** Resumable page sessions currently retained by this process. */
    public static final String PAGE_SESSIONS_ACTIVE = "rsp.page.sessions.active";

    /** Metric updates rejected because the name, type, or counter delta was invalid. */
    public static final String METRIC_UPDATES_REJECTED = "rsp.metrics.updates.rejected";

    private static final MetricCatalog FRAMEWORK_CATALOG = MetricCatalog.of(
            MetricDescriptor.counter(SEGMENT_CREATED, "1", "Component segments created", "SegmentCreated"),
            MetricDescriptor.counter(SEGMENT_UNMOUNTED, "1", "Component segments unmounted", "SegmentUnmounted"),
            MetricDescriptor.counter(SEGMENT_UPDATE_DROPPED_UNMOUNTED,
                                     "1",
                                     "Updates dropped for unmounted component segments",
                                     "SegmentUpdatesDroppedUnmounted"),
            MetricDescriptor.counter(HTTP_REQUESTS, "1", "HTTP requests received", "HttpRequests"),
            MetricDescriptor.counter(HTTP_FAILURES, "1", "HTTP request failures", "HttpFailures"),
            MetricDescriptor.gauge(WEB_SOCKET_CONNECTIONS_ACTIVE,
                                   "1",
                                   "Currently active WebSocket connections",
                                   "WebSocketConnectionsActive"),
            MetricDescriptor.gauge(PAGE_SESSIONS_ACTIVE,
                                   "1",
                                   "Currently retained resumable page sessions",
                                   "PageSessionsActive"),
            MetricDescriptor.counter(METRIC_UPDATES_REJECTED,
                                     "1",
                                     "Rejected metric updates",
                                     "MetricUpdatesRejected"));

    /** Returns the fixed allow-list of framework runtime metrics. */
    public static MetricCatalog frameworkCatalog() {
        return FRAMEWORK_CATALOG;
    }
}
