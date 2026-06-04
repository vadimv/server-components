package rsp.pbt;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.pbt.PbtTestSupport.captureFailureMessage;
import static rsp.pbt.PbtTestSupport.withConfig;

/**
 * Tier 0 — trusted base: the {@link Property} runner tested deterministically (pinned seed/tries).
 * Includes the headline planted-bug-shrinks-to-the-known-minimum checks.
 */
class PropertyRunnerTests {

    @Test
    void a_true_invariant_passes() {
        withConfig(1, 300, () ->
                Property.forAll(Gen.integers(0, 1_000)).check(x -> assertTrue(x + 1 > x)));
    }

    @Test
    void a_passing_property_runs_exactly_the_configured_number_of_tries() {
        withConfig(1, 250, () -> {
            final AtomicInteger runs = new AtomicInteger();
            Property.forAll(Gen.integers(0, 100)).check(x -> runs.incrementAndGet());
            assertEquals(250, runs.get());
        });
    }

    @Test
    void planted_int_bug_shrinks_to_the_known_minimum() {
        withConfig(1, 200, () -> {
            final String message = captureFailureMessage(() ->
                    Property.forAll(Gen.integers(0, 100)).check(x -> assertTrue(x < 10)));
            assertTrue(message.endsWith("Counterexample: 10"), message);
        });
    }

    @Test
    void planted_list_bug_shrinks_to_the_singleton_counterexample() {
        withConfig(1, 300, () -> {
            final String message = captureFailureMessage(() ->
                    Property.forAll(Gen.integers(0, 9).list(0, 7)).check(list -> assertFalse(list.contains(7))));
            assertTrue(message.endsWith("Counterexample: [7]"), message);
        });
    }

    @Test
    void the_same_seed_reproduces_the_same_failure() {
        withConfig(12_345, 200, () -> {
            final String first = captureFailureMessage(() ->
                    Property.forAll(Gen.integers(0, 100)).check(x -> assertTrue(x < 10)));
            final String second = captureFailureMessage(() ->
                    Property.forAll(Gen.integers(0, 100)).check(x -> assertTrue(x < 10)));
            assertEquals(first, second);
            assertTrue(first.contains("seed=12345"), first);
        });
    }

    @Test
    void a_different_seed_still_finds_a_counterexample() {
        withConfig(98_765, 200, () ->
                assertThrows(AssertionError.class, () ->
                        Property.forAll(Gen.integers(0, 100)).check(x -> assertTrue(x < 10))));
    }

    @Test
    void assume_discards_do_not_count_toward_tries() {
        withConfig(1, 100, () -> {
            final AtomicInteger valid = new AtomicInteger();
            Property.forAll(Gen.integers(0, 100)).check(x -> {
                Property.assume(x % 2 == 0);
                valid.incrementAndGet();
                assertTrue(x % 2 == 0);
            });
            assertEquals(100, valid.get());
        });
    }

    @Test
    void a_property_that_discards_everything_gives_up_rather_than_hanging() {
        withConfig(1, 50, () -> {
            final String message = captureFailureMessage(() ->
                    Property.forAll(Gen.integers(0, 100)).check(x -> Property.assume(false)));
            assertTrue(message.contains("gave up"), message);
        });
    }

    @Test
    void two_argument_properties_are_supported() {
        withConfig(1, 200, () ->
                Property.forAll(Gen.integers(0, 50), Gen.integers(0, 50))
                        .check((a, b) -> assertEquals(a + b, b + a)));
    }

    @Test
    void classify_prints_a_distribution_summary() {
        final String out = captureStdout(() -> withConfig(1, 300, () ->
                Property.forAll(Gen.integers(0, 100))
                        .classify("low (<10)", x -> x < 10)
                        .classify("high (>=90)", x -> x >= 90)
                        .check(x -> assertTrue(x >= 0 && x <= 100))));
        assertTrue(out.contains("PBT distribution (300 tests)"), out);
        assertTrue(out.contains("low (<10)") && out.contains("high (>=90)"), out);
    }

    @Test
    void collect_buckets_inputs_in_the_summary() {
        final String out = captureStdout(() -> withConfig(1, 200, () ->
                Property.forAll(Gen.booleans())
                        .collect(b -> b ? "true" : "false")
                        .check(b -> { })));
        assertTrue(out.contains("PBT distribution"), out);
        assertTrue(out.contains("true") && out.contains("false"), out);
    }

    @Test
    void classify_reports_labels_that_never_match_as_zero_percent() {
        // A never-matched classify label is the key signal ("the generator produced no such case"),
        // so it must still appear in the summary at 0.0%.
        final String out = captureStdout(() -> withConfig(1, 100, () ->
                Property.forAll(Gen.integers(0, 100))
                        .classify("never", x -> false)
                        .check(x -> { })));
        assertTrue(out.contains("never 0.0%"), out);
    }

    @Test
    void classification_uses_the_original_input_even_if_the_body_mutates_it() {
        // Every generated list is non-empty (min size 1); the body clears it in place. The summary
        // must reflect the input as generated (100% non-empty), not the mutated, emptied state.
        final String out = captureStdout(() -> withConfig(1, 100, () ->
                Property.forAll(Gen.integers(0, 9).list(1, 5))
                        .classify("nonEmpty", list -> !list.isEmpty())
                        .check(java.util.List::clear)));
        assertTrue(out.contains("nonEmpty 100.0%"), out);
    }

    @Test
    void diagnostics_do_not_run_for_discarded_inputs() {
        // The labeller is unsafe on empty strings, but those are discarded by assume(...), so the
        // run must complete without the tagger error escaping.
        final String out = captureStdout(() -> withConfig(1, 50, () ->
                Property.forAll(Gen.alpha(0, 5))
                        .collect(s -> String.valueOf(s.charAt(0)))
                        .check(s -> {
                            Property.assume(!s.isEmpty());
                            assertTrue(s.length() >= 1);
                        })));
        assertTrue(out.contains("PBT distribution"), out);
    }

    @Test
    void diagnostic_assertions_do_not_run_for_discarded_inputs() {
        // Assertions throw AssertionError rather than RuntimeException; discarded inputs should defer
        // and then ignore those failures too.
        final String out = captureStdout(() -> withConfig(1, 50, () ->
                Property.forAll(Gen.alpha(0, 5))
                        .collect(s -> {
                            assertTrue(!s.isEmpty());
                            return s.charAt(0);
                        })
                        .check(s -> {
                            Property.assume(!s.isEmpty());
                            assertTrue(s.length() >= 1);
                        })));
        assertTrue(out.contains("PBT distribution"), out);
    }

    @Test
    void a_tagger_that_throws_on_an_accepted_input_surfaces_as_an_error() {
        // charAt(10) is out of range for every accepted (length 1..3) input — a non-total labeller.
        final IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                withConfig(1, 50, () ->
                        Property.forAll(Gen.alpha(1, 3))
                                .collect(s -> String.valueOf(s.charAt(10)))
                                .check(s -> { })));
        assertTrue(e.getMessage().contains("accepted input"), e.getMessage());
    }

    private static String captureStdout(final Runnable body) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true));
        try {
            body.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }
}
