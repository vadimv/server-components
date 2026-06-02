package rsp.pbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A generator of random values carrying an integrated shrink tree. A {@code Gen<T>} is a pure
 * function of {@code (random, size)} producing a {@link Shrinkable}; the same seed reproduces the
 * same value, which is what makes property failures replayable.
 *
 * <p>This is the programmatic replacement for jqwik's {@code Arbitrary} / {@code Arbitraries} /
 * {@code Combinators}. Generators are values, not annotations — properties are plain JUnit
 * {@code @Test} methods that build a {@code Gen} and hand it to {@link Property}.
 *
 * <p>{@code size} is advisory: every generator here is explicitly bounded (ranges, max sizes,
 * recursion depth), so generation does not depend on {@code size}. It is threaded through for
 * forward compatibility.
 */
@FunctionalInterface
public interface Gen<T> {

    Shrinkable<T> generate(Random random, int size);

    // --- instance combinators (shrink-tree preserving) ---

    default <R> Gen<R> map(final Function<? super T, ? extends R> f) {
        return (random, size) -> generate(random, size).map(f);
    }

    /** Keeps generating until the value satisfies {@code pred}; shrinks stay within {@code pred}. */
    default Gen<T> filter(final Predicate<? super T> pred) {
        return (random, size) -> {
            for (int attempt = 0; attempt < 1_000; attempt++) {
                final Shrinkable<T> s = generate(random, size);
                if (pred.test(s.value())) {
                    return s.filterShrinks(pred);
                }
            }
            throw new GenerationException("filter: no generated value satisfied the predicate after 1000 attempts");
        };
    }

    /**
     * Monadic bind. Shrinking is best-effort: it shrinks the outer value (regenerating the inner
     * with a fixed seed for stability) and the inner value, but does not jointly minimise both.
     * No current property test relies on user-level {@code flatMap} shrinking.
     */
    default <R> Gen<R> flatMap(final Function<? super T, ? extends Gen<R>> f) {
        return (random, size) -> {
            final Shrinkable<T> outer = generate(random, size);
            final long innerSeed = random.nextLong();
            return flatMapShrinkable(outer, f, innerSeed, size);
        };
    }

    /** A list of this generator's values with length in {@code [min, max]}. */
    default Gen<List<T>> list(final int min, final int max) {
        return listOf(this, min, max, false);
    }

    /** A list of distinct values with length in {@code [min, max]}. */
    default Gen<List<T>> listUnique(final int min, final int max) {
        return listOf(this, min, max, true);
    }

    // ====================================================================
    // Static factories (1:1 with the jqwik surface this project used)
    // ====================================================================

    /** {@code Arbitraries.integers().between(min, max)}. Shrinks toward 0 clamped into range. */
    static Gen<Integer> integers(final int min, final int max) {
        requireRange(min, max);
        final int target = clampInt(0, min, max);
        return (random, size) -> shrinkIntTowards(target, randomInt(random, min, max));
    }

    /** {@code Arbitraries.longs().between(min, max)}. Shrinks toward 0 clamped into range. */
    static Gen<Long> longs(final long min, final long max) {
        if (min > max) throw new IllegalArgumentException("min > max: " + min + " > " + max);
        final long target = Math.max(min, Math.min(max, 0L));
        return (random, size) -> shrinkLongTowards(target, randomLong(random, min, max));
    }

    /** Booleans; {@code true} shrinks to {@code false}. */
    static Gen<Boolean> booleans() {
        return (random, size) -> random.nextBoolean()
                ? Shrinkable.of(Boolean.TRUE, () -> Stream.of(Shrinkable.leaf(Boolean.FALSE)))
                : Shrinkable.leaf(Boolean.FALSE);
    }

    /** {@code Arbitraries.strings().alpha().ofMinLength(min).ofMaxLength(max)} (chars {@code a-z}). */
    static Gen<String> alpha(final int minLength, final int maxLength) {
        requireRange(minLength, maxLength);
        if (minLength < 0) throw new IllegalArgumentException("minLength < 0: " + minLength);
        final Gen<Character> chars = (random, size) -> shrinkCharTowards('a', (char) ('a' + randomInt(random, 0, 25)));
        return (random, size) -> {
            final int length = randomInt(random, minLength, maxLength);
            final List<Shrinkable<Character>> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(chars.generate(random, size));
            }
            return shrinkableList(elements, minLength).map(Gen::charsToString);
        };
    }

    /** {@code Arbitraries.of(values...)}. Picks uniformly; shrinks toward the first value. */
    @SafeVarargs
    static <T> Gen<T> of(final T... values) {
        if (values.length == 0) throw new IllegalArgumentException("Gen.of requires at least one value");
        final List<T> vs = List.of(values);
        return (random, size) -> shrinkChoice(vs, randomInt(random, 0, vs.size() - 1));
    }

    /** {@code Arbitraries.oneOf(gens...)}. Picks one generator uniformly and delegates to it. */
    @SafeVarargs
    static <T> Gen<T> oneOf(final Gen<? extends T>... gens) {
        if (gens.length == 0) throw new IllegalArgumentException("Gen.oneOf requires at least one generator");
        return (random, size) -> {
            final int i = randomInt(random, 0, gens.length - 1);
            @SuppressWarnings("unchecked")
            final Shrinkable<T> s = (Shrinkable<T>) gens[i].generate(random, size);
            return s;
        };
    }

    /** {@code Arbitraries.maps(keys, values).ofMaxSize(maxSize)} with distinct keys. */
    static <K, V> Gen<Map<K, V>> maps(final Gen<K> keys, final Gen<V> values, final int maxSize) {
        if (maxSize < 0) throw new IllegalArgumentException("maxSize < 0: " + maxSize);
        final Gen<Map.Entry<K, V>> entries = combine(keys, values, Map::entry);
        return (random, size) -> {
            final int n = randomInt(random, 0, maxSize);
            final LinkedHashMap<K, V> seen = new LinkedHashMap<>();
            final List<Shrinkable<Map.Entry<K, V>>> elements = new ArrayList<>();
            int attempts = 0;
            while (elements.size() < n && attempts < n * 10 + 10) {
                attempts++;
                final Shrinkable<Map.Entry<K, V>> e = entries.generate(random, size);
                if (!seen.containsKey(e.value().getKey())) {
                    seen.put(e.value().getKey(), e.value().getValue());
                    elements.add(e);
                }
            }
            return shrinkableList(elements, 0).map(Gen::entriesToMap);
        };
    }

    /**
     * {@code Arbitraries.recursive(base, expand, depth)}: {@code expand} applied {@code depth}
     * times over {@code base}. Branches terminate naturally when an inner {@code list} yields zero
     * elements.
     */
    static <T> Gen<T> recursive(final Supplier<Gen<T>> base,
                                final Function<Gen<T>, Gen<T>> expand,
                                final int depth) {
        if (depth <= 0) {
            return base.get();
        }
        return expand.apply(recursive(base, expand, depth - 1));
    }

    // --- applicative combination (correct tuple shrinking by interleaving child trees) ---

    /** {@code Combinators.combine(a, b).as(f)}. */
    static <A, B, R> Gen<R> combine(final Gen<A> a, final Gen<B> b,
                                    final BiFunction<? super A, ? super B, ? extends R> f) {
        return (random, size) -> combine2(a.generate(random, size), b.generate(random, size), f);
    }

    /** {@code Combinators.combine(a, b, c).as(f)}. */
    static <A, B, C, R> Gen<R> combine(final Gen<A> a, final Gen<B> b, final Gen<C> c,
                                       final Fn3<? super A, ? super B, ? super C, ? extends R> f) {
        return (random, size) -> combine3(a.generate(random, size), b.generate(random, size),
                c.generate(random, size), f);
    }

    /** {@code Combinators.combine(a, b, c, d).as(f)}. */
    static <A, B, C, D, R> Gen<R> combine(final Gen<A> a, final Gen<B> b, final Gen<C> c, final Gen<D> d,
                                          final Fn4<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
        return (random, size) -> combine4(a.generate(random, size), b.generate(random, size),
                c.generate(random, size), d.generate(random, size), f);
    }

    // ====================================================================
    // Internals
    // ====================================================================

    private static <A, B, R> Shrinkable<R> combine2(final Shrinkable<A> a, final Shrinkable<B> b,
                                                    final BiFunction<? super A, ? super B, ? extends R> f) {
        return Shrinkable.of(f.apply(a.value(), b.value()), () -> Stream.concat(
                a.shrinks().map(s -> combine2(s, b, f)),
                b.shrinks().map(s -> combine2(a, s, f))));
    }

    private static <A, B, C, R> Shrinkable<R> combine3(final Shrinkable<A> a, final Shrinkable<B> b,
                                                       final Shrinkable<C> c,
                                                       final Fn3<? super A, ? super B, ? super C, ? extends R> f) {
        return Shrinkable.of(f.apply(a.value(), b.value(), c.value()), () -> Stream.concat(
                a.shrinks().map(s -> combine3(s, b, c, f)),
                Stream.concat(
                        b.shrinks().map(s -> combine3(a, s, c, f)),
                        c.shrinks().map(s -> combine3(a, b, s, f)))));
    }

    private static <A, B, C, D, R> Shrinkable<R> combine4(final Shrinkable<A> a, final Shrinkable<B> b,
                                                          final Shrinkable<C> c, final Shrinkable<D> d,
                                                          final Fn4<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
        return Shrinkable.of(f.apply(a.value(), b.value(), c.value(), d.value()), () -> Stream.concat(
                a.shrinks().map(s -> combine4(s, b, c, d, f)),
                Stream.concat(
                        b.shrinks().map(s -> combine4(a, s, c, d, f)),
                        Stream.concat(
                                c.shrinks().map(s -> combine4(a, b, s, d, f)),
                                d.shrinks().map(s -> combine4(a, b, c, s, f))))));
    }

    private static <T, R> Shrinkable<R> flatMapShrinkable(final Shrinkable<T> outer,
                                                          final Function<? super T, ? extends Gen<R>> f,
                                                          final long innerSeed, final int size) {
        final Shrinkable<R> inner = f.apply(outer.value()).generate(new Random(innerSeed), size);
        return Shrinkable.of(inner.value(), () -> Stream.concat(
                outer.shrinks().map(os -> flatMapShrinkable(os, f, innerSeed, size)),
                inner.shrinks()));
    }

    private static <T> Gen<List<T>> listOf(final Gen<T> element,
                                           final int min,
                                           final int max,
                                           final boolean unique) {
        requireRange(min, max);
        if (min < 0) throw new IllegalArgumentException("min size < 0: " + min);
        return (random, size) -> {
            final int length = randomInt(random, min, max);
            final List<Shrinkable<T>> elements = new ArrayList<>(length);
            if (unique) {
                final List<T> seen = new ArrayList<>();
                final int maxAttempts = Math.max(length, min) * 100 + 100;
                int attempts = 0;
                while (elements.size() < length && attempts < maxAttempts) {
                    attempts++;
                    final Shrinkable<T> e = element.generate(random, size);
                    if (!seen.contains(e.value())) {
                        seen.add(e.value());
                        elements.add(e);
                    }
                }
                // If the element domain can't supply min distinct values the request is
                // unsatisfiable — fail loudly rather than returning an undersized list. (Reaching
                // between min and the randomly chosen length is fine: still within [min, max].)
                if (elements.size() < min) {
                    throw new GenerationException("listUnique: generated only " + elements.size()
                            + " distinct element(s) but at least " + min + " were required"
                            + " — the element generator's value domain is too small");
                }
            } else {
                for (int i = 0; i < length; i++) {
                    elements.add(element.generate(random, size));
                }
            }
            final Shrinkable<List<T>> shrinkable = shrinkableList(elements, min);
            return unique ? shrinkable.filterShrinks(Gen::allDistinct) : shrinkable;
        };
    }

    private static <T> boolean allDistinct(final List<T> list) {
        return list.size() == new java.util.HashSet<>(list).size();
    }

    /** Builds a list shrinkable: shrink by removing elements (down to {@code minSize}) and by shrinking each element. */
    private static <T> Shrinkable<List<T>> shrinkableList(final List<Shrinkable<T>> elements, final int minSize) {
        final List<T> values = new ArrayList<>(elements.size());
        for (final Shrinkable<T> e : elements) {
            values.add(e.value());
        }
        return Shrinkable.of(values, () -> {
            final Stream.Builder<Shrinkable<List<T>>> out = Stream.builder();
            if (elements.size() > minSize) {
                for (int i = 0; i < elements.size(); i++) {
                    final List<Shrinkable<T>> removed = new ArrayList<>(elements);
                    removed.remove(i);
                    out.add(shrinkableList(removed, minSize));
                }
            }
            for (int i = 0; i < elements.size(); i++) {
                final int idx = i;
                elements.get(i).shrinks().forEach(sh -> {
                    final List<Shrinkable<T>> replaced = new ArrayList<>(elements);
                    replaced.set(idx, sh);
                    out.add(shrinkableList(replaced, minSize));
                });
            }
            return out.build();
        });
    }

    private static <T> Shrinkable<T> shrinkChoice(final List<T> values, final int index) {
        return Shrinkable.of(values.get(index), () -> {
            if (index == 0) {
                return Stream.empty();
            }
            final Stream.Builder<Shrinkable<T>> out = Stream.builder();
            out.add(shrinkChoice(values, 0));
            if (index - 1 > 0) {
                out.add(shrinkChoice(values, index - 1));
            }
            return out.build();
        });
    }

    private static Shrinkable<Integer> shrinkIntTowards(final int target, final int value) {
        return Shrinkable.of(value, () -> intCandidates(target, value).mapToObj(c -> shrinkIntTowards(target, c)));
    }

    private static Shrinkable<Long> shrinkLongTowards(final long target, final long value) {
        return Shrinkable.of(value, () -> longCandidates(target, value).mapToObj(c -> shrinkLongTowards(target, c)));
    }

    private static Shrinkable<Character> shrinkCharTowards(final char target, final char value) {
        return Shrinkable.of(value, () -> intCandidates(target, value).mapToObj(c -> shrinkCharTowards(target, (char) c)));
    }

    /** The halving sequence from {@code target} toward {@code value}: {@code target} first, then closer. */
    private static IntStream intCandidates(final int target, final int value) {
        if (value == target) {
            return IntStream.empty();
        }
        final List<Integer> candidates = new ArrayList<>();
        for (long delta = (long) value - target; delta != 0; delta /= 2) {
            final int candidate = (int) (value - delta);
            if (candidate != value && (candidates.isEmpty() || candidates.get(candidates.size() - 1) != candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates.stream().mapToInt(Integer::intValue);
    }

    private static java.util.stream.LongStream longCandidates(final long target, final long value) {
        if (value == target) {
            return java.util.stream.LongStream.empty();
        }
        final List<Long> candidates = new ArrayList<>();
        for (long delta = value - target; delta != 0; delta /= 2) {
            final long candidate = value - delta;
            if (candidate != value && (candidates.isEmpty() || candidates.get(candidates.size() - 1) != candidate)) {
                candidates.add(candidate);
            }
        }
        return candidates.stream().mapToLong(Long::longValue);
    }

    private static String charsToString(final List<Character> chars) {
        final StringBuilder sb = new StringBuilder(chars.size());
        for (final char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static <K, V> Map<K, V> entriesToMap(final List<Map.Entry<K, V>> entries) {
        final LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (final Map.Entry<K, V> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return map;
    }

    private static int randomInt(final Random random, final int min, final int max) {
        final long range = (long) max - (long) min + 1L;
        return (int) (min + Math.floorMod(random.nextLong(), range));
    }

    private static long randomLong(final Random random, final long min, final long max) {
        final long range = max - min + 1L;
        if (range <= 0L) {
            // Width exceeds the long range, so (max - min + 1) overflowed. Reject-sample into
            // [min, max]: for the true full range the first draw is always accepted; for a partial
            // overflowing range (e.g. [0, Long.MAX_VALUE]) this keeps the result in bounds.
            long v;
            do {
                v = random.nextLong();
            } while (v < min || v > max);
            return v;
        }
        return min + Math.floorMod(random.nextLong(), range);
    }

    private static int clampInt(final int target, final int min, final int max) {
        return Math.max(min, Math.min(max, target));
    }

    private static void requireRange(final int min, final int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max: " + min + " > " + max);
        }
    }

    /** Thrown when a generator cannot produce a value (e.g. an over-constrained {@link #filter}). */
    final class GenerationException extends RuntimeException {
        GenerationException(final String message) {
            super(message);
        }
    }
}
