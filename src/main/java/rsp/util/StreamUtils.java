package rsp.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtils {

    private StreamUtils() {}

    /**
     * Converts an {@link java.util.Iterator} to {@link java.util.stream.Stream}.
     */
    public static <T> Stream<T> iterate(final Iterator<? extends T> iterator) {
        final int characteristics = Spliterator.ORDERED | Spliterator.IMMUTABLE;
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, characteristics), false);
    }

    /**
     * Zips two streams. The resulting stream is truncated to the shorter of the two input streams.
     */
    public static <L, R, T> Stream<T> zip(final Stream<L> leftStream, final Stream<R> rightStream, final BiFunction<L, R, T> combiner) {
        final Spliterator<L> lefts = leftStream.spliterator();
        final Spliterator<R> rights = rightStream.spliterator();
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(Long.min(lefts.estimateSize(), rights.estimateSize()), lefts.characteristics() & rights.characteristics()) {
            @Override
            public boolean tryAdvance(final Consumer<? super T> action) {
                return lefts.tryAdvance(left->rights.tryAdvance(right->action.accept(combiner.apply(left, right))));
            }
        }, leftStream.isParallel() || rightStream.isParallel());
    }

    /**
     * Zips the specified stream with its indices.
     */
    public static <T> Stream<Map.Entry<Integer, T>> zipWithIndex(final Stream<? extends T> stream) {
        return iterate(new Iterator<>() {
            private final Iterator<? extends T> streamIterator = stream.iterator();
            private int index = 0;

            @Override
            public boolean hasNext() {
                return streamIterator.hasNext();
            }

            @Override
            public Map.Entry<Integer, T> next() {
                return new AbstractMap.SimpleImmutableEntry<>(index++, streamIterator.next());
            }
        });
    }

    /**
     * Returns a stream consisting of the results of applying the given two-arguments function to the elements of this stream.
     * The first argument of the function is the element index and the second one - the element value.
     */
    public static <T, R> Stream<R> mapWithIndex(final Stream<? extends T> stream, final BiFunction<Integer, ? super T, ? extends R> mapper) {
        return zipWithIndex(stream).map(entry -> mapper.apply(entry.getKey(), entry.getValue()));
    }

    /**
     * Converts a list of CompletableFuture to a CompletableFuture of a list
     */
    public static<T> CompletableFuture<List<T>> sequence(final List<CompletableFuture<T>> listOfCompletableFutures) {
        return CompletableFuture.allOf(listOfCompletableFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> listOfCompletableFutures.stream()
                                                        .map(CompletableFuture::join)
                                                        .toList());
    }
}
