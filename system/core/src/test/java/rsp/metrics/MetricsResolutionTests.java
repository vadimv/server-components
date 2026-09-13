package rsp.metrics;

import org.junit.jupiter.api.Test;
import rsp.component.ComponentContext;
import rsp.component.TestLookup;

import static org.junit.jupiter.api.Assertions.*;

class MetricsResolutionTests {

    @Test
    void from_empty_context_returns_noop() {
        final Metrics m = Metrics.from(new ComponentContext());
        assertSame(Metrics.noop(), m);
    }

    @Test
    void from_context_with_recording_returns_it() {
        final RecordingMetrics rec = new RecordingMetrics();
        final ComponentContext ctx = new ComponentContext().with(Metrics.class, rec);
        assertSame(rec, Metrics.from(ctx));
    }

    @Test
    void from_empty_lookup_returns_noop() {
        assertSame(Metrics.noop(), Metrics.from(new TestLookup()));
    }

    @Test
    void from_lookup_with_recording_returns_it() {
        final RecordingMetrics rec = new RecordingMetrics();
        final TestLookup lookup = new TestLookup().withData(Metrics.class, rec);

        assertSame(rec, Metrics.from(lookup));
    }

    @Test
    void noop_methods_do_not_throw() {
        final Metrics m = Metrics.noop();
        m.incrementCounter("anything");
        m.incrementCounter("anything", 100);
        m.setGauge("g", 42);
        // No exception, no observable side effect.
    }

    @Test
    void noop_is_singleton() {
        assertSame(Metrics.noop(), Metrics.noop());
    }
}
