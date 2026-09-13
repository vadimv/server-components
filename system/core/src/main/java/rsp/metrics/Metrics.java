package rsp.metrics;

import rsp.component.ComponentContext;

/**
 * Framework metrics SPI. Single counter / gauge interface, no tags, no histograms.
 * <p>
 * Default {@link NoOpMetrics} is zero-cost — virtual calls to empty methods, JIT-inlined.
 * Tests can use {@link RecordingMetrics}; production applications can use
 * {@link MetricRegistry} or provide an adapter. A {@code Metrics} instance can
 * also be placed into a page session's root {@link ComponentContext}.
 * <p>
 * Metric names are part of the public stability contract — see {@link MetricNames}.
 * Names must have bounded cardinality; do not embed session ids, component ids, or
 * other unbounded values into the name string.
 */
public interface Metrics {

    /**
     * Increment a counter by one.
     * @param name a metric name from {@link MetricNames}
     */
    void incrementCounter(String name);

    /**
     * Increment a counter by a non-negative delta. Implementations may reject
     * negative updates; prefer separate counters for opposing events.
     */
    void incrementCounter(String name, long delta);

    /**
     * Set a gauge to an absolute value.
     */
    void setGauge(String name, long value);

    /**
     * @return the no-op singleton; safe to call from any thread.
     */
    static Metrics noop() {
        return NoOpMetrics.INSTANCE;
    }

    /**
     * Resolve the metrics implementation from a component context, falling back to
     * {@link #noop()} if no implementation is registered.
     */
    static Metrics from(final ComponentContext context) {
        final Metrics m = context.get(Metrics.class);
        return m != null ? m : NoOpMetrics.INSTANCE;
    }
}
