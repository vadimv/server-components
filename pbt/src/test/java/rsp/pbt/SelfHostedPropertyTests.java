package rsp.pbt;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.pbt.PbtTestSupport.withConfig;

/**
 * Tier 1 — self-hosted: the harness exercised through its own {@link Property#forAll} (pinned
 * seed). Green here only counts when Tier 0 ({@link GenTests}, {@link PropertyRunnerTests}) is
 * green, so a self-test is never the sole evidence. The headline is
 * {@link #the_runner_shrinks_to_a_sound_local_minimum()} — the harness proving its own shrinking.
 */
class SelfHostedPropertyTests {

    @Test
    void generator_range_invariants_hold_as_properties() {
        withConfig(2, 500, () -> {
            Property.forAll(Gen.integers(-50, 50)).check(x -> assertTrue(x >= -50 && x <= 50));
            Property.forAll(Gen.longs(1, 9)).check(x -> assertTrue(x >= 1 && x <= 9));
            Property.forAll(Gen.alpha(0, 8)).check(s ->
                    assertTrue(s.length() <= 8 && s.chars().allMatch(c -> c >= 'a' && c <= 'z')));
            Property.forAll(Gen.integers(0, 20).listUnique(0, 10)).check(list ->
                    assertEquals(list.size(), new java.util.HashSet<>(list).size()));
            Property.forAll(Gen.maps(Gen.alpha(1, 3), Gen.integers(0, 5), 4)).check(map ->
                    assertTrue(map.size() <= 4));
        });
    }

    @Test
    void combine_distributes_over_its_components_as_a_property() {
        withConfig(3, 500, () ->
                Property.forAll(Gen.combine(Gen.integers(0, 100), Gen.integers(0, 100), (a, b) -> new int[]{a, b}))
                        .check(pair -> assertTrue(pair[0] >= 0 && pair[0] <= 100 && pair[1] >= 0 && pair[1] <= 100)));
    }

    /**
     * Shrink-soundness meta-property: for many failing inputs, the minimiser's result must
     * (a) really fail the property and (b) have no failing shrink (a local minimum) — and for this
     * monotone threshold predicate the local minimum is the exact global minimum (137).
     * The harness proving its own shrinking is sound, using itself.
     */
    @Test
    void the_runner_shrinks_to_a_sound_local_minimum() {
        final Gen<Integer> gen = Gen.integers(0, 1_000);
        final Predicate<Integer> property = x -> x < 137; // fails for x >= 137
        int exercised = 0;
        for (long seed = 0; seed < 300; seed++) {
            final Shrinkable<Integer> sh = gen.generate(new Random(seed), 50);
            if (!Property.fails(property, sh.value())) {
                continue;
            }
            exercised++;
            final Property.Minimized<Integer> minimized = Property.minimize(sh, property);
            final int minimal = minimized.shrinkable().value();

            // (a) the reported counterexample is a genuine failure
            assertTrue(Property.fails(property, minimal), "minimal " + minimal + " should fail the property");
            // (b) no shrink of it fails — it is a local minimum
            minimized.shrinkable().shrinks().forEach(s ->
                    assertFalse(Property.fails(property, s.value()), "shrink " + s.value() + " should not fail"));
            // for this monotone threshold predicate the local minimum is the global minimum
            assertEquals(137, minimal, "minimal failing value should be the threshold");
        }
        assertTrue(exercised > 50, "expected many failing inputs to exercise shrinking, got " + exercised);
    }
}
