package rsp.metrics;

/**
 * Zero-cost {@link Metrics} implementation. All methods are empty so the JIT
 * inlines them away. The default when no other implementation is registered.
 */
public final class NoOpMetrics implements Metrics {

    static final NoOpMetrics INSTANCE = new NoOpMetrics();

    private NoOpMetrics() {}

    @Override
    public void incrementCounter(final String name) {}

    @Override
    public void incrementCounter(final String name, final long delta) {}

    @Override
    public void setGauge(final String name, final long value) {}
}
