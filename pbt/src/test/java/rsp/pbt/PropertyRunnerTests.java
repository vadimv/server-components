package rsp.pbt;

import org.junit.jupiter.api.Test;

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
}
