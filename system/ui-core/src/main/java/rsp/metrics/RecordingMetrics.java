package rsp.metrics;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory {@link Metrics} implementation for tests. Thread-safe.
 * Counters use {@link LongAdder} (lock-free, contention-tolerant);
 * gauges use {@link AtomicLong}.
 */
public final class RecordingMetrics implements Metrics {

    private final ConcurrentMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    @Override
    public void incrementCounter(final String name) {
        incrementCounter(name, 1L);
    }

    @Override
    public void incrementCounter(final String name, final long delta) {
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    @Override
    public void setGauge(final String name, final long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /**
     * @return current counter value, or 0 if the counter was never incremented.
     */
    public long counter(final String name) {
        final LongAdder a = counters.get(name);
        return a == null ? 0L : a.sum();
    }

    /**
     * @return current gauge value, or 0 if the gauge was never set.
     */
    public long gauge(final String name) {
        final AtomicLong a = gauges.get(name);
        return a == null ? 0L : a.get();
    }

    /**
     * @return all counter names that have been touched.
     */
    public Set<String> counterNames() {
        return Set.copyOf(counters.keySet());
    }

    /**
     * @return all gauge names that have been set.
     */
    public Set<String> gaugeNames() {
        return Set.copyOf(gauges.keySet());
    }

    /**
     * Snapshot all counters as an immutable map.
     */
    public Map<String, Long> snapshotCounters() {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().sum()));
    }

    /**
     * Reset all counters and gauges. Useful between scenarios in a single test class.
     */
    public void reset() {
        counters.clear();
        gauges.clear();
    }
}
