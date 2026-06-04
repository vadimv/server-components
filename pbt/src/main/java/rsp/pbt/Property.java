package rsp.pbt;

import java.util.Iterator;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Runs property checks over {@link Gen}erated inputs and minimizes any counterexample by walking
 * the integrated shrink tree. This is the programmatic, non-annotation replacement for jqwik's
 * {@code @Property}: a property is an ordinary JUnit {@code @Test} that ends with
 * {@code Property.forAll(gen).check(value -> { ... })}.
 *
 * <h2>Configuration (system properties)</h2>
 * <ul>
 *   <li>{@code -Dpbt.tries=N} — number of passing inputs required (default {@value #DEFAULT_TRIES}).</li>
 *   <li>{@code -Dpbt.seed=S} — base seed; omit for a random seed. The failing seed is printed so a
 *       failure can be replayed deterministically.</li>
 * </ul>
 *
 * <p>A check fails if the body throws (an {@link AssertionError} from a JUnit assertion, or any
 * {@link RuntimeException}) or a {@link Predicate} body returns {@code false}. Call
 * {@link #assume(boolean)} to discard an unwanted input without counting it as a try.
 */
public final class Property {

    /** Default number of passing inputs required, matching jqwik's default. */
    public static final int DEFAULT_TRIES = 1000;

    private Property() {
    }

    /**
     * Begins a single-argument property.
     *
     * @param a   the input generator
     * @param <A> the input type
     * @return a builder whose {@code check} runs the property
     */
    public static <A> ForAll1<A> forAll(final Gen<A> a) {
        return new ForAll1<>(a);
    }

    /**
     * Begins a two-argument property; the inputs are drawn and shrunk jointly.
     *
     * @param a   the first input generator
     * @param b   the second input generator
     * @param <A> the first input type
     * @param <B> the second input type
     * @return a builder whose {@code check} runs the property
     */
    public static <A, B> ForAll2<A, B> forAll(final Gen<A> a, final Gen<B> b) {
        return new ForAll2<>(a, b);
    }

    /**
     * Discards the current input (it does not count toward tries) unless {@code condition} holds.
     * Replaces jqwik's {@code Assume.that}.
     *
     * @param condition the precondition the input must satisfy to be kept
     */
    public static void assume(final boolean condition) {
        if (!condition) {
            throw new DiscardException();
        }
    }

    /**
     * A property over a single generated value.
     *
     * @param <A> the input type
     */
    public static final class ForAll1<A> {
        private final Gen<A> gen;

        private ForAll1(final Gen<A> gen) {
            this.gen = gen;
        }

        /**
         * Runs the property: draws inputs and applies {@code body} to each. The body asserts
         * (throwing on failure); a thrown {@link AssertionError}/{@link RuntimeException} is a
         * counterexample, which is then shrunk to a minimal value. Use
         * {@link Property#assume(boolean)} to discard unwanted inputs.
         *
         * @param body the assertion to run against each generated value
         * @throws AssertionError on the first failing input, reporting the shrunk counterexample and seed
         */
        public void check(final Consumer<? super A> body) {
            run(gen, a -> {
                body.accept(a);
                return true;
            }, String::valueOf);
        }
    }

    /**
     * A property over a pair of generated values.
     *
     * @param <A> the first input type
     * @param <B> the second input type
     */
    public static final class ForAll2<A, B> {
        private final Gen<A> a;
        private final Gen<B> b;

        private ForAll2(final Gen<A> a, final Gen<B> b) {
            this.a = a;
            this.b = b;
        }

        /**
         * Runs the property against each generated pair; see {@link ForAll1#check}.
         *
         * @param body the assertion to run against each generated pair
         * @throws AssertionError on the first failing pair, reporting the shrunk counterexample and seed
         */
        @SuppressWarnings("unchecked")
        public void check(final BiConsumer<? super A, ? super B> body) {
            run(pairGen(), arr -> {
                body.accept((A) arr[0], (B) arr[1]);
                return true;
            }, Property::renderPair);
        }

        private Gen<Object[]> pairGen() {
            return Gen.combine(a, b, (x, y) -> new Object[]{x, y});
        }
    }

    // ====================================================================
    // Runner
    // ====================================================================

    private static <T> void run(final Gen<T> gen,
                                final Predicate<T> property,
                                final Function<? super T, String> render) {
        final long seed = configuredSeed();
        final int tries = configuredTries();
        final int maxDiscards = Math.max(10, tries * 10);
        final Random seedSource = new Random(seed);

        int completed = 0;
        int discards = 0;
        while (completed < tries) {
            final int size = sizeFor(completed, tries);
            // Generation happens outside the evaluation guard so a generator error is never
            // mistaken for a property failure.
            final Shrinkable<T> sh = gen.generate(new Random(seedSource.nextLong()), size);
            final Outcome outcome = evaluate(property, sh.value());
            if (outcome.discarded()) {
                if (++discards > maxDiscards) {
                    throw new AssertionError("PBT gave up after " + discards + " discards (only "
                            + completed + " of " + tries + " inputs satisfied assumptions, seed=" + seed + ")");
                }
                continue;
            }
            completed++;
            if (outcome.failed()) {
                throw shrinkAndReport(sh, property, render, seed, completed);
            }
        }
    }

    private static <T> AssertionError shrinkAndReport(final Shrinkable<T> failing, final Predicate<T> property,
                                                      final Function<? super T, String> render,
                                                      final long seed, final int completed) {
        final Minimized<T> minimized = minimize(failing, property);
        final T minimal = minimized.shrinkable().value();
        final Throwable cause = evaluate(property, minimal).error();
        final String message = "PBT failed (seed=" + seed + ", after " + completed + " tests, "
                + minimized.steps() + " shrinks). Counterexample: " + render.apply(minimal);
        return new AssertionError(message, cause);
    }

    /**
     * Greedily walks the shrink tree, repeatedly taking the first still-failing child, until no
     * child fails. Package-private so the harness self-tests can assert shrink soundness directly.
     *
     * @param failing  a shrinkable whose value fails {@code property}
     * @param property the check that {@code failing} violates
     * @param <T>      the input type
     * @return the minimal still-failing shrinkable and the number of shrink steps taken
     */
    static <T> Minimized<T> minimize(final Shrinkable<T> failing, final Predicate<T> property) {
        Shrinkable<T> current = failing;
        int steps = 0;
        boolean improved = true;
        while (improved) {
            improved = false;
            final Iterator<Shrinkable<T>> it = current.shrinks().iterator();
            while (it.hasNext()) {
                final Shrinkable<T> candidate = it.next();
                if (evaluate(property, candidate.value()).failed()) {
                    current = candidate;
                    steps++;
                    improved = true;
                    break;
                }
            }
        }
        return new Minimized<>(current, steps);
    }

    /**
     * Whether {@code value} fails {@code property} under the same rules the runner uses (a thrown
     * assertion/exception or a {@code false} return counts as failure; a discard does not).
     *
     * @param property the check
     * @param value    the input to evaluate
     * @param <T>      the input type
     * @return {@code true} if {@code value} is a counterexample
     */
    static <T> boolean fails(final Predicate<T> property, final T value) {
        return evaluate(property, value).failed();
    }

    /**
     * Result of {@link #minimize}.
     *
     * @param shrinkable the minimal still-failing shrinkable
     * @param steps      the number of shrink steps taken to reach it
     * @param <T>        the input type
     */
    record Minimized<T>(Shrinkable<T> shrinkable, int steps) {
    }

    private static <T> Outcome evaluate(final Predicate<T> property, final T value) {
        try {
            return property.test(value) ? Outcome.PASS : Outcome.failed(null);
        } catch (final DiscardException d) {
            return Outcome.DISCARD;
        } catch (final AssertionError | RuntimeException t) {
            return Outcome.failed(t);
        }
    }

    private static int sizeFor(final int completed, final int tries) {
        if (tries <= 1) {
            return 1;
        }
        return 1 + (int) (99L * completed / (tries - 1)); // advisory ramp 1..100
    }

    private static long configuredSeed() {
        final String s = System.getProperty("pbt.seed");
        return s != null ? Long.parseLong(s.trim()) : new Random().nextLong();
    }

    private static int configuredTries() {
        final String t = System.getProperty("pbt.tries");
        return t != null ? Integer.parseInt(t.trim()) : DEFAULT_TRIES;
    }

    private static String renderPair(final Object[] arr) {
        return "(" + arr[0] + ", " + arr[1] + ")";
    }

    private record Outcome(Kind kind, Throwable error) {
        private enum Kind { PASS, FAIL, DISCARD }

        private static final Outcome PASS = new Outcome(Kind.PASS, null);
        private static final Outcome DISCARD = new Outcome(Kind.DISCARD, null);

        private static Outcome failed(final Throwable error) {
            return new Outcome(Kind.FAIL, error);
        }

        private boolean discarded() {
            return kind == Kind.DISCARD;
        }

        private boolean failed() {
            return kind == Kind.FAIL;
        }
    }

    private static final class DiscardException extends RuntimeException {
        private DiscardException() {
            super(null, null, false, false);
        }
    }
}
