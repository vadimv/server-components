package rsp.app.posts.services;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OuSpikeSampleSourceTests {

    @Test
    void same_seed_produces_identical_sequence() {
        OuSpikeSampleSource a = OuSpikeSampleSource.commentsRateDefaults(new Random(42L));
        OuSpikeSampleSource b = OuSpikeSampleSource.commentsRateDefaults(new Random(42L));

        for (int i = 0; i < 200; i++) {
            assertEquals(a.next(), b.next(), "divergence at index " + i);
        }
    }

    @Test
    void different_seeds_diverge() {
        OuSpikeSampleSource a = OuSpikeSampleSource.commentsRateDefaults(new Random(1L));
        OuSpikeSampleSource b = OuSpikeSampleSource.commentsRateDefaults(new Random(2L));

        boolean diverged = false;
        for (int i = 0; i < 50; i++) {
            if (a.next() != b.next()) {
                diverged = true;
                break;
            }
        }
        assertTrue(diverged, "two different seeds should diverge within 50 samples");
    }

    @Test
    void values_stay_non_negative_and_bounded() {
        OuSpikeSampleSource source = OuSpikeSampleSource.commentsRateDefaults(new Random(7L));

        for (int i = 0; i < 5000; i++) {
            int v = source.next();
            assertTrue(v >= 0, "value should be non-negative, got " + v);
            assertTrue(v < 1000, "value should be reasonably bounded, got " + v);
        }
    }

    @Test
    void mean_stays_close_to_configured_mu_when_no_spikes() {
        OuSpikeSampleSource source = new OuSpikeSampleSource(
                120.0, 0.08, 6.0,
                0.0, 0.0, 0.0, 0.0,
                new Random(99L));

        long sum = 0;
        int count = 5000;
        for (int i = 0; i < count; i++) {
            sum += source.next();
        }
        double mean = sum / (double) count;
        assertTrue(mean > 100 && mean < 140, "mean should be near 120, got " + mean);
    }

    @Test
    void steady_state_steps_are_small_without_spikes() {
        OuSpikeSampleSource source = new OuSpikeSampleSource(
                120.0, 0.08, 6.0,
                0.0, 0.0, 0.0, 0.0,
                new Random(11L));

        int previous = source.next();
        int maxDelta = 0;
        for (int i = 0; i < 2000; i++) {
            int current = source.next();
            maxDelta = Math.max(maxDelta, Math.abs(current - previous));
            previous = current;
        }
        assertTrue(maxDelta < 40,
                "OU steady-state delta should stay small, observed max delta " + maxDelta);
    }

    @Test
    void spikes_eventually_appear_when_enabled() {
        OuSpikeSampleSource source = OuSpikeSampleSource.commentsRateDefaults(new Random(5L));

        int max = 0;
        for (int i = 0; i < 1000; i++) {
            max = Math.max(max, source.next());
        }
        assertTrue(max > 180, "at least one spike should have appeared in 1000 samples, max was " + max);
    }

    @Test
    void rejects_invalid_parameters() {
        Random r = new Random(0);
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 0.0, 6, 0.05, 100, 200, 0.7, r));
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 1.5, 6, 0.05, 100, 200, 0.7, r));
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 0.1, -1, 0.05, 100, 200, 0.7, r));
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 0.1, 6, -0.1, 100, 200, 0.7, r));
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 0.1, 6, 0.05, 200, 100, 0.7, r));
        assertThrows(IllegalArgumentException.class,
                () -> new OuSpikeSampleSource(120, 0.1, 6, 0.05, 100, 200, 1.0, r));
    }

    @Test
    void initial_window_size_defaults_to_max() {
        IntSampleSource source = OuSpikeSampleSource.commentsRateDefaults(new Random(0L));
        assertEquals(30, source.initialWindowSize(30));
    }

    @Test
    void cycling_source_initial_window_clamped_to_values_length() {
        IntSampleSource source = new CyclingSampleSource(List.of(1, 2, 3));
        assertEquals(3, source.initialWindowSize(10));
        assertEquals(2, source.initialWindowSize(2));
    }

    @Test
    void cycling_source_cycles_in_order() {
        CyclingSampleSource source = new CyclingSampleSource(List.of(10, 20, 30));
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            seen.add(source.next());
        }
        assertEquals(List.of(10, 20, 30, 10, 20, 30, 10), seen);
    }
}
