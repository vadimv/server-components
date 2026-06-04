package rsp.pbt;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rsp.pbt.PbtTestSupport.shrinkValues;

/**
 * Tier 0 — trusted base: generators tested deterministically (fixed seed), with no harness-runner
 * logic under test. These anchor everything above them.
 */
class GenTests {

    @Test
    void generation_is_deterministic_for_a_fixed_seed() {
        assertEquals(Gen.integers(0, 100).generate(new Random(42), 50).value(),
                     Gen.integers(0, 100).generate(new Random(42), 50).value());
        assertEquals(Gen.alpha(0, 10).generate(new Random(7), 50).value(),
                     Gen.alpha(0, 10).generate(new Random(7), 50).value());
    }

    @Test
    void integers_seed_their_boundary_values() {
        // Edge-case seeding must surface min, max and the shrink target (0) within a modest sample.
        final Set<Integer> seen = new HashSet<>();
        final Random source = new Random(1);
        final Gen<Integer> g = Gen.integers(-50, 50);
        for (int i = 0; i < 500; i++) {
            seen.add(g.generate(new Random(source.nextLong()), 50).value());
        }
        assertTrue(seen.contains(-50), "min boundary not generated");
        assertTrue(seen.contains(50), "max boundary not generated");
        assertTrue(seen.contains(0), "zero (shrink target) boundary not generated");
    }

    @Test
    void list_seeds_empty_and_max_length() {
        boolean empty = false;
        boolean full = false;
        final Random source = new Random(2);
        final Gen<List<Integer>> g = Gen.integers(0, 9).list(0, 5);
        for (int i = 0; i < 500 && !(empty && full); i++) {
            final int sz = g.generate(new Random(source.nextLong()), 50).value().size();
            empty |= sz == 0;
            full |= sz == 5;
        }
        assertTrue(empty, "empty list boundary not generated");
        assertTrue(full, "max-length list boundary not generated");
    }

    @Test
    void alpha_seeds_empty_and_max_length() {
        boolean empty = false;
        boolean full = false;
        final Random source = new Random(3);
        final Gen<String> g = Gen.alpha(0, 8);
        for (int i = 0; i < 500 && !(empty && full); i++) {
            final int len = g.generate(new Random(source.nextLong()), 50).value().length();
            empty |= len == 0;
            full |= len == 8;
        }
        assertTrue(empty, "empty string boundary not generated");
        assertTrue(full, "max-length string boundary not generated");
    }

    @Test
    void integers_are_within_bounds() {
        for (long seed = 0; seed < 200; seed++) {
            final int v = Gen.integers(-30, 70).generate(new Random(seed), 50).value();
            assertTrue(v >= -30 && v <= 70, "out of range: " + v);
        }
    }

    @Test
    void longs_stay_in_bounds_even_for_overflowing_partial_ranges() {
        // (max - min + 1) overflows long here, so randomLong takes its reject-sampling path; the
        // results must still respect the requested bounds (regression for a raw-draw fallback).
        for (long seed = 0; seed < 500; seed++) {
            final long lower = Gen.longs(0, Long.MAX_VALUE).generate(new Random(seed), 50).value();
            assertTrue(lower >= 0, "longs(0, MAX) produced negative: " + lower);

            final long full = Gen.longs(Long.MIN_VALUE, Long.MAX_VALUE).generate(new Random(seed), 50).value();
            assertTrue(full >= Long.MIN_VALUE && full <= Long.MAX_VALUE, "out of full range: " + full);
        }
    }

    @Test
    void integer_shrinks_lead_strictly_toward_zero_and_stay_in_range() {
        for (long seed = 0; seed < 200; seed++) {
            final Shrinkable<Integer> sh = Gen.integers(0, 1000).generate(new Random(seed), 50);
            final int v = sh.value();
            sh.shrinks().forEach(s -> {
                assertTrue(Math.abs(s.value()) < Math.abs(v), "shrink " + s.value() + " not closer to 0 than " + v);
                assertTrue(s.value() >= 0 && s.value() <= 1000, "shrink out of range: " + s.value());
            });
            if (v > 0) {
                assertEquals(0, shrinkValues(sh).get(0), "first shrink candidate should be the target (0)");
            }
        }
    }

    @Test
    void alpha_respects_length_bounds_and_charset() {
        for (long seed = 0; seed < 200; seed++) {
            final String s = Gen.alpha(2, 8).generate(new Random(seed), 50).value();
            assertTrue(s.length() >= 2 && s.length() <= 8, "bad length: '" + s + "'");
            assertTrue(s.chars().allMatch(c -> c >= 'a' && c <= 'z'), "non a-z char in: '" + s + "'");
        }
    }

    @Test
    void list_unique_has_no_duplicates_including_after_shrinking() {
        for (long seed = 0; seed < 200; seed++) {
            final Shrinkable<List<Integer>> sh = Gen.integers(0, 12).listUnique(0, 8).generate(new Random(seed), 50);
            assertDistinct(sh.value());
            sh.shrinks().limit(50).forEach(s -> assertDistinct(s.value()));
        }
    }

    @Test
    void list_unique_always_meets_the_minimum_size() {
        // Domain has 4 distinct values; min 3 is satisfiable, so every result has size >= 3.
        final Gen<List<Integer>> g = Gen.of(0, 1, 2, 3).listUnique(3, 4);
        for (long seed = 0; seed < 200; seed++) {
            assertTrue(g.generate(new Random(seed), 50).value().size() >= 3, "below minimum size");
        }
    }

    @Test
    void list_unique_fails_loudly_when_the_domain_is_too_small() {
        // Only one distinct value available but two are required: unsatisfiable -> throw, not [1].
        final Gen<List<Integer>> g = Gen.of(1).listUnique(2, 2);
        assertThrows(Gen.GenerationException.class, () -> g.generate(new Random(1), 50));
    }

    @Test
    void of_picks_in_range_and_shrinks_toward_first() {
        final Gen<String> g = Gen.of("a", "b", "c", "d");
        for (long seed = 0; seed < 100; seed++) {
            final Shrinkable<String> sh = g.generate(new Random(seed), 50);
            assertTrue(List.of("a", "b", "c", "d").contains(sh.value()));
            if (!sh.value().equals("a")) {
                assertTrue(shrinkValues(sh).contains("a"), "should be able to shrink toward first value");
            }
        }
    }

    @Test
    void map_satisfies_the_functor_law() {
        final Gen<Integer> g = Gen.integers(0, 100);
        final Function<Integer, Integer> f = x -> x + 1;
        final Function<Integer, String> h = x -> "n" + x;
        for (long seed = 0; seed < 100; seed++) {
            final Shrinkable<String> composed = g.map(f).map(h).generate(new Random(seed), 30);
            final Shrinkable<String> fused = g.map(x -> h.apply(f.apply(x))).generate(new Random(seed), 30);
            assertEquals(fused.value(), composed.value());
            assertEquals(shrinkValues(fused), shrinkValues(composed), "map must thread the shrink tree");
        }
    }

    private static <T> void assertDistinct(final List<T> list) {
        assertFalse(list.size() != new java.util.HashSet<>(list).size(), "duplicates in " + list);
    }
}
