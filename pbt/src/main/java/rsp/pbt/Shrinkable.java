package rsp.pbt;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An integrated-shrinking rose tree: a generated {@code value} together with a lazily-computed
 * stream of strictly "smaller" variants ({@link #shrinks()}). Generators build these so that
 * shrinking composes for free through {@link #map(Function)}, {@code combine}, {@code list}, etc.
 *
 * <p>The shrink stream is produced fresh on each {@link #shrinks()} call (streams are single-use)
 * and is never forced until requested — this keeps recursive generators from eagerly unfolding.
 */
public final class Shrinkable<T> {

    private final T value;
    private final Supplier<Stream<Shrinkable<T>>> shrinks;

    private Shrinkable(final T value, final Supplier<Stream<Shrinkable<T>>> shrinks) {
        this.value = value;
        this.shrinks = shrinks;
    }

    /** A value with no smaller variants. */
    public static <T> Shrinkable<T> leaf(final T value) {
        return new Shrinkable<>(value, Stream::empty);
    }

    /** A value with a lazily-computed stream of smaller variants. */
    public static <T> Shrinkable<T> of(final T value, final Supplier<Stream<Shrinkable<T>>> shrinks) {
        return new Shrinkable<>(value, shrinks);
    }

    public T value() {
        return value;
    }

    /** The smaller variants of this value, computed lazily and freshly on each call. */
    public Stream<Shrinkable<T>> shrinks() {
        return shrinks.get();
    }

    /** Maps the value and, lazily, every variant in the shrink tree. */
    public <R> Shrinkable<R> map(final Function<? super T, ? extends R> f) {
        return new Shrinkable<>(f.apply(value), () -> shrinks.get().map(s -> s.map(f)));
    }

    /**
     * Prunes the shrink tree to variants whose value satisfies {@code pred} (recursively).
     * Used by {@link Gen#filter(Predicate)} so a filtered generator never offers a shrink that
     * violates the predicate.
     */
    public Shrinkable<T> filterShrinks(final Predicate<? super T> pred) {
        return new Shrinkable<>(value,
                () -> shrinks.get().filter(s -> pred.test(s.value())).map(s -> s.filterShrinks(pred)));
    }
}
