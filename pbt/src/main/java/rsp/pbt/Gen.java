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
 * A generator of random values carrying an integrated shrink tree. A {@code Gen<T>} consumes a
 * {@link Random} and a size hint to produce a {@link Shrinkable}; the same seed reproduces the same
 * value, which is what makes property failures replayable.
 *
 * <p>This is the programmatic replacement for jqwik's {@code Arbitrary} / {@code Arbitraries} /
 * {@code Combinators}. Generators are values, not annotations — properties are plain JUnit
 * {@code @Test} methods that build a {@code Gen} and hand it to {@link Property}.
 *
 * <p>{@code size} is advisory: every generator here is explicitly bounded (ranges, max sizes,
 * recursion depth), so generation does not depend on {@code size}. It is threaded through for
 * forward compatibility.
 *
 * <p><b>Edge-case seeding.</b> The leaf generators bias roughly one draw in {@value #EDGE_ONE_IN}
 * toward a boundary value — {@code min}/{@code max}/the shrink target for numbers, empty/min/max
 * length for strings, lists and maps — so off-by-one and empty-collection bugs are exercised far
 * more often than uniform sampling would manage. Because the bias lives in the value distribution
 * it propagates for free through {@code map}, {@code combine}, {@code list} and {@code recursive}.
 * It is probabilistic, not a guarantee.
 *
 * @param <T> the type of value produced
 */
@FunctionalInterface
public interface Gen<T> {

    /**
     * Produces one value together with its shrink tree. Deterministic for the same {@code Random}
     * state and {@code size}; generation advances the supplied random source.
     *
     * @param random the source of randomness for this draw
     * @param size   advisory size hint, unused by the built-in generators (see the type javadoc)
     * @return the generated value paired with its lazily-computed smaller variants
     */
    Shrinkable<T> generate(Random random, int size);

    // --- instance combinators (shrink-tree preserving) ---

    /**
     * Transforms each generated value with {@code f}, carrying the shrink tree through unchanged.
     *
     * @param f   maps a generated value to the result type
     * @param <R> the mapped result type
     * @return a generator of {@code f}-applied values
     */
    default <R> Gen<R> map(final Function<? super T, ? extends R> f) {
        return (random, size) -> generate(random, size).map(f);
    }

    /**
     * Restricts generation to values satisfying {@code pred}, resampling on rejection; surviving
     * shrinks also satisfy {@code pred}.
     *
     * @param pred the predicate a generated value must satisfy
     * @return a generator yielding only values for which {@code pred} holds
     * @throws GenerationException if no value satisfies {@code pred} within 1000 attempts
     */
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
     * Monadic bind: feeds each generated value into {@code f} to choose the next generator.
     * Shrinking is best-effort — it shrinks the outer value (regenerating the inner with a fixed
     * seed for stability) and the inner value, but does not jointly minimise both. No current
     * property test relies on user-level {@code flatMap} shrinking.
     *
     * @param f   chooses the next generator from a produced value
     * @param <R> the type produced by the chosen generator
     * @return a generator that draws an outer value then a dependent inner value
     */
    default <R> Gen<R> flatMap(final Function<? super T, ? extends Gen<R>> f) {
        return (random, size) -> {
            final Shrinkable<T> outer = generate(random, size);
            final long innerSeed = random.nextLong();
            return flatMapShrinkable(outer, f, innerSeed, size);
        };
    }

    /**
     * A list of this generator's values with length in {@code [min, max]}; shrinks toward shorter
     * lists and smaller elements.
     *
     * @param min minimum list length (inclusive, {@code >= 0})
     * @param max maximum list length (inclusive, {@code >= min})
     * @return a generator of bounded-length lists
     */
    default Gen<List<T>> list(final int min, final int max) {
        return listOf(this, min, max, false);
    }

    /**
     * Like {@link #list(int, int)} but with distinct elements (by {@code equals}).
     *
     * @param min minimum list length (inclusive, {@code >= 0})
     * @param max maximum list length (inclusive, {@code >= min})
     * @return a generator of bounded-length lists of distinct values
     * @throws GenerationException if generation cannot draw {@code min} distinct values within its
     *                             attempt budget
     */
    default Gen<List<T>> listUnique(final int min, final int max) {
        return listOf(this, min, max, true);
    }

    // ====================================================================
    // Static factories (1:1 with the jqwik surface this project used)
    // ====================================================================

    /**
     * {@code Arbitraries.integers().between(min, max)}. Shrinks toward 0 clamped into range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive, {@code >= min})
     * @return a generator of ints in {@code [min, max]}
     */
    static Gen<Integer> integers(final int min, final int max) {
        requireRange(min, max);
        final int target = clampInt(0, min, max);
        return (random, size) -> {
            final int value = drawEdge(random) ? edgeInt(random, min, max, target) : randomInt(random, min, max);
            return shrinkIntTowards(target, value);
        };
    }

    /**
     * {@code Arbitraries.longs().between(min, max)}. Shrinks toward 0 clamped into range.
     *
     * @param min minimum value (inclusive)
     * @param max maximum value (inclusive, {@code >= min})
     * @return a generator of longs in {@code [min, max]}
     */
    static Gen<Long> longs(final long min, final long max) {
        if (min > max) throw new IllegalArgumentException("min > max: " + min + " > " + max);
        final long target = Math.max(min, Math.min(max, 0L));
        return (random, size) -> {
            final long value = drawEdge(random) ? edgeLong(random, min, max, target) : randomLong(random, min, max);
            return shrinkLongTowards(target, value);
        };
    }

    /**
     * Booleans; {@code true} shrinks to {@code false}.
     *
     * @return a generator of uniformly random booleans
     */
    static Gen<Boolean> booleans() {
        return (random, size) -> random.nextBoolean()
                ? Shrinkable.of(Boolean.TRUE, () -> Stream.of(Shrinkable.leaf(Boolean.FALSE)))
                : Shrinkable.leaf(Boolean.FALSE);
    }

    /**
     * {@code Arbitraries.strings().alpha().ofMinLength(min).ofMaxLength(max)} over chars {@code a-z}
     * (lowercase only). Shrinks toward shorter strings and chars toward {@code 'a'}.
     *
     * @param minLength minimum string length (inclusive, {@code >= 0})
     * @param maxLength maximum string length (inclusive, {@code >= minLength})
     * @return a generator of lowercase alphabetic strings
     */
    static Gen<String> alpha(final int minLength, final int maxLength) {
        requireRange(minLength, maxLength);
        if (minLength < 0) throw new IllegalArgumentException("minLength < 0: " + minLength);
        final Gen<Character> chars = (random, size) -> shrinkCharTowards('a', (char) ('a' + randomInt(random, 0, 25)));
        return (random, size) -> {
            final int length = drawEdge(random) ? (random.nextBoolean() ? minLength : maxLength)
                                                : randomInt(random, minLength, maxLength);
            final List<Shrinkable<Character>> elements = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                elements.add(chars.generate(random, size));
            }
            return shrinkableList(elements, minLength).map(Gen::charsToString);
        };
    }

    /**
     * {@code Arbitraries.of(values...)}. Picks uniformly; shrinks toward the first value.
     *
     * @param values the candidate values (at least one)
     * @param <T>    the value type
     * @return a generator choosing uniformly among {@code values}
     */
    @SafeVarargs
    static <T> Gen<T> of(final T... values) {
        if (values.length == 0) throw new IllegalArgumentException("Gen.of requires at least one value");
        final List<T> vs = List.of(values);
        return (random, size) -> shrinkChoice(vs, randomInt(random, 0, vs.size() - 1));
    }

    /**
     * {@code Arbitraries.oneOf(gens...)}. Picks one generator uniformly and delegates to it.
     *
     * @param gens the candidate generators (at least one)
     * @param <T>  the common value type
     * @return a generator that defers to a uniformly chosen member of {@code gens}
     */
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

    /**
     * {@code Arbitraries.maps(keys, values).ofMaxSize(maxSize)} with distinct keys.
     *
     * @param keys    generator for map keys (duplicates are dropped, so the result may be smaller)
     * @param values  generator for map values
     * @param maxSize maximum entry count (inclusive, {@code >= 0})
     * @param <K>     the key type
     * @param <V>     the value type
     * @return a generator of maps with up to {@code maxSize} distinct-key entries
     */
    static <K, V> Gen<Map<K, V>> maps(final Gen<K> keys, final Gen<V> values, final int maxSize) {
        if (maxSize < 0) throw new IllegalArgumentException("maxSize < 0: " + maxSize);
        final Gen<Map.Entry<K, V>> entries = combine(keys, values, Map::entry);
        return (random, size) -> {
            final int n = drawEdge(random) ? (random.nextBoolean() ? 0 : maxSize)
                                           : randomInt(random, 0, maxSize);
            final LinkedHashMap<K, V> seen = new LinkedHashMap<>();
            final List<Shrinkable<Map.Entry<K, V>>> elements = new ArrayList<>();
            int attempts = 0;
            while (elements.size() < n && attempts < distinctAttempts(n)) {
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
     * {@code Arbitraries.recursive(base, expand, depth)}: {@code expand} applied {@code depth} times
     * over {@code base}. Branches terminate naturally when an inner {@code list} yields zero
     * elements.
     *
     * @param base   supplies the leaf (depth-0) generator
     * @param expand wraps a child generator into a deeper one
     * @param depth  number of {@code expand} applications ({@code <= 0} returns {@code base})
     * @param <T>    the generated type
     * @return a generator producing recursively nested structures up to {@code depth}
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

    /**
     * {@code Combinators.combine(a, b).as(f)}.
     *
     * @param a   first component generator
     * @param b   second component generator
     * @param f   combines the two values into a result
     * @param <A> first component type
     * @param <B> second component type
     * @param <R> result type
     * @return a generator of {@code f}-combined pairs, shrinking both components
     */
    static <A, B, R> Gen<R> combine(final Gen<A> a, final Gen<B> b,
                                    final BiFunction<? super A, ? super B, ? extends R> f) {
        return (random, size) -> combine2(a.generate(random, size), b.generate(random, size), f);
    }

    /**
     * {@code Combinators.combine(a, b, c).as(f)}.
     *
     * @param a   first component generator
     * @param b   second component generator
     * @param c   third component generator
     * @param f   combines the three values into a result
     * @param <A> first component type
     * @param <B> second component type
     * @param <C> third component type
     * @param <R> result type
     * @return a generator of {@code f}-combined triples, shrinking all components
     */
    static <A, B, C, R> Gen<R> combine(final Gen<A> a, final Gen<B> b, final Gen<C> c,
                                       final Fn3<? super A, ? super B, ? super C, ? extends R> f) {
        return (random, size) -> combine3(a.generate(random, size), b.generate(random, size),
                c.generate(random, size), f);
    }

    /**
     * {@code Combinators.combine(a, b, c, d).as(f)}.
     *
     * @param a   first component generator
     * @param b   second component generator
     * @param c   third component generator
     * @param d   fourth component generator
     * @param f   combines the four values into a result
     * @param <A> first component type
     * @param <B> second component type
     * @param <C> third component type
     * @param <D> fourth component type
     * @param <R> result type
     * @return a generator of {@code f}-combined quadruples, shrinking all components
     */
    static <A, B, C, D, R> Gen<R> combine(final Gen<A> a,
                                          final Gen<B> b,
                                          final Gen<C> c,
                                          final Gen<D> d,
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

    /**
     * Builds the {@link #flatMap} result tree: the chosen inner value, whose shrinks are the outer
     * shrinks (each re-bound through {@code f} at the fixed {@code innerSeed}) followed by the
     * inner's own shrinks.
     */
    private static <T, R> Shrinkable<R> flatMapShrinkable(final Shrinkable<T> outer,
                                                          final Function<? super T, ? extends Gen<R>> f,
                                                          final long innerSeed, final int size) {
        final Shrinkable<R> inner = f.apply(outer.value()).generate(new Random(innerSeed), size);
        return Shrinkable.of(inner.value(), () -> Stream.concat(
                outer.shrinks().map(os -> flatMapShrinkable(os, f, innerSeed, size)),
                inner.shrinks()));
    }

    /**
     * Shared implementation of {@link #list} and {@link #listUnique}. Unique lists use a bounded
     * retry loop; if it cannot draw {@code min} distinct values, generation fails instead of
     * returning an undersized list.
     */
    private static <T> Gen<List<T>> listOf(final Gen<T> element,
                                           final int min,
                                           final int max,
                                           final boolean unique) {
        requireRange(min, max);
        if (min < 0) throw new IllegalArgumentException("min size < 0: " + min);
        return (random, size) -> {
            final int length = drawEdge(random) ? (random.nextBoolean() ? min : max)
                                                : randomInt(random, min, max);
            final List<Shrinkable<T>> elements = new ArrayList<>(length);
            if (unique) {
                final List<T> seen = new ArrayList<>();
                final int maxAttempts = distinctAttempts(Math.max(length, min));
                int attempts = 0;
                while (elements.size() < length && attempts < maxAttempts) {
                    attempts++;
                    final Shrinkable<T> e = element.generate(random, size);
                    if (!seen.contains(e.value())) {
                        seen.add(e.value());
                        elements.add(e);
                    }
                }
                // Reaching between min and the randomly chosen length is fine: still within [min, max].
                if (elements.size() < min) {
                    throw new GenerationException("listUnique: generated only " + elements.size()
                            + " distinct element(s) but at least " + min + " were required"
                            + " — generation could not draw enough distinct values");
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

    /**
     * Builds a list shrinkable: shrink by removing elements (down to {@code minSize}) and by
     * shrinking each element in place.
     *
     * @param elements the per-element shrinkables, in order
     * @param minSize  the smallest list length removals may reach
     * @param <T>      the element type
     * @return a shrinkable list whose tree explores shorter lists and smaller elements
     */
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

    /** Shrinks a choice toward the first value, with an intermediate step through the previous value. */
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

    /**
     * The halving sequence from {@code target} toward {@code value}: {@code target} first, then
     * successively closer points, ending just shy of {@code value}.
     *
     * @param target the value shrinking converges toward
     * @param value  the value being shrunk
     * @return candidate ints between {@code target} (inclusive) and {@code value} (exclusive), or
     *         empty when {@code value == target}
     */
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

    /** Roughly one draw in this many is biased toward a boundary value (see the type javadoc). */
    int EDGE_ONE_IN = 8;

    /**
     * Attempt budget for collecting distinct values (unique lists, distinct map keys):
     * {@code targetSize * }{@value #DISTINCT_ATTEMPTS_PER_ELEMENT}{@code  + }{@value #DISTINCT_ATTEMPTS_FLOOR}.
     * The per-element factor absorbs collisions; the floor gives small targets a fair chance. When
     * the budget is exhausted, {@code listUnique} fails and {@code maps} returns a smaller map.
     */
    int DISTINCT_ATTEMPTS_PER_ELEMENT = 100;

    /** Baseline distinct-sampling attempts independent of target size; see {@link #DISTINCT_ATTEMPTS_PER_ELEMENT}. */
    int DISTINCT_ATTEMPTS_FLOOR = 100;

    /** Whether this draw should yield a boundary value rather than a uniform one. */
    private static boolean drawEdge(final Random random) {
        return random.nextInt(EDGE_ONE_IN) == 0;
    }

    /** The distinct-sampling attempt budget for a target of {@code targetSize} distinct values. */
    private static int distinctAttempts(final int targetSize) {
        return targetSize * DISTINCT_ATTEMPTS_PER_ELEMENT + DISTINCT_ATTEMPTS_FLOOR;
    }

    /** A boundary int chosen uniformly among the distinct values {@code {min, max, target}}. */
    private static int edgeInt(final Random random, final int min, final int max, final int target) {
        final List<Integer> edges = new ArrayList<>(3);
        edges.add(min);
        if (max != min) edges.add(max);
        if (target != min && target != max) edges.add(target);
        return edges.get(random.nextInt(edges.size()));
    }

    /** A boundary long chosen uniformly among the distinct values {@code {min, max, target}}. */
    private static long edgeLong(final Random random, final long min, final long max, final long target) {
        final List<Long> edges = new ArrayList<>(3);
        edges.add(min);
        if (max != min) edges.add(max);
        if (target != min && target != max) edges.add(target);
        return edges.get(random.nextInt(edges.size()));
    }

    /** A uniform int in {@code [min, max]}; widened to {@code long} so the range can't overflow. */
    private static int randomInt(final Random random, final int min, final int max) {
        final long range = (long) max - (long) min + 1L;
        return (int) (min + Math.floorMod(random.nextLong(), range));
    }

    /** A uniform long in {@code [min, max]}, reject-sampling when the span overflows {@code long}. */
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
