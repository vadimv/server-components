package rsp.pbt;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A generated value plus a lazy tree of smaller variants. The runner walks this tree to minimise a
 * failing example. Generators build shrinkables so shrinking composes through
 * {@link #map(Function)}, {@code combine}, {@code list}, and similar combinators.
 *
 * <p>The shrink stream is produced fresh on each {@link #shrinks()} call (streams are single-use)
 * and is never forced until requested — this keeps recursive generators from eagerly unfolding.
 *
 * @param <T> the type of the held value
 */
public final class Shrinkable<T> {

    private final T value;
    private final Supplier<Stream<Shrinkable<T>>> shrinks;

    private Shrinkable(final T value, final Supplier<Stream<Shrinkable<T>>> shrinks) {
        this.value = value;
        this.shrinks = shrinks;
    }

    /**
     * A leaf: a value with no smaller variants.
     *
     * @param value the held value
     * @param <T>   the value type
     * @return a shrinkable that offers no shrinks
     */
    public static <T> Shrinkable<T> leaf(final T value) {
        return new Shrinkable<>(value, Stream::empty);
    }

    /**
     * A value paired with a lazily-computed stream of smaller variants.
     *
     * @param value   the held value
     * @param shrinks supplies the smaller variants on demand (re-invoked per {@link #shrinks()} call)
     * @param <T>     the value type
     * @return a shrinkable with the given shrink tree
     */
    public static <T> Shrinkable<T> of(final T value, final Supplier<Stream<Shrinkable<T>>> shrinks) {
        return new Shrinkable<>(value, shrinks);
    }

    /** @return the held value */
    public T value() {
        return value;
    }

    /** @return the smaller variants of this value, computed lazily and freshly on each call */
    public Stream<Shrinkable<T>> shrinks() {
        return shrinks.get();
    }

    /**
     * Maps the value and, lazily, every variant in the shrink tree.
     *
     * @param f   the value transform
     * @param <R> the mapped value type
     * @return a shrinkable of mapped values with the structurally identical shrink tree
     */
    public <R> Shrinkable<R> map(final Function<? super T, ? extends R> f) {
        return new Shrinkable<>(f.apply(value), () -> shrinks.get().map(s -> s.map(f)));
    }

    /**
     * Prunes the shrink tree to variants whose value satisfies {@code pred} (recursively).
     * Used by {@link Gen#filter(Predicate)} so a filtered generator never offers a shrink that
     * violates the predicate.
     *
     * @param pred the predicate every retained shrink's value must satisfy
     * @return a shrinkable with the same value but a {@code pred}-respecting shrink tree
     */
    public Shrinkable<T> filterShrinks(final Predicate<? super T> pred) {
        return new Shrinkable<>(value,
                () -> shrinks.get().filter(s -> pred.test(s.value())).map(s -> s.filterShrinks(pred)));
    }
}
