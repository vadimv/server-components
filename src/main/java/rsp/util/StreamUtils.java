package rsp.util;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


public final class StreamUtils {

    /**
     * Converts an {@link java.util.Iterator} to {@link java.util.stream.Stream}.
     */
    public static <T> Stream<T> iterate(Iterator<? extends T> iterator) {
        int characteristics = Spliterator.ORDERED | Spliterator.IMMUTABLE;
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, characteristics), false);
    }

    /**
     * Zips two streams. The resulting stream is truncated to the shorter of the two input streams.
     */
    public static <L, R, T> Stream<T> zip(Stream<L> leftStream, Stream<R> rightStream, BiFunction<L, R, T> combiner) {
        Spliterator<L> lefts = leftStream.spliterator();
        Spliterator<R> rights = rightStream.spliterator();
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<T>(Long.min(lefts.estimateSize(), rights.estimateSize()), lefts.characteristics() & rights.characteristics()) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                return lefts.tryAdvance(left->rights.tryAdvance(right->action.accept(combiner.apply(left, right))));
            }
        }, leftStream.isParallel() || rightStream.isParallel());
    }

    /**
     * Zips the specified stream with its indices.
     */
    public static <T> Stream<Map.Entry<Integer, T>> zipWithIndex(Stream<? extends T> stream) {
        return iterate(new Iterator<Map.Entry<Integer, T>>() {
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
    public static <T, R> Stream<R> mapWithIndex(Stream<? extends T> stream, BiFunction<Integer, ? super T, ? extends R> mapper) {
        return zipWithIndex(stream).map(entry -> mapper.apply(entry.getKey(), entry.getValue()));
    }

    /**
     * Converts a list of CompletableFuture to a CompletableFuture of a list
     */
    public static<T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> listOfCompletableFutures) {
        return CompletableFuture.allOf(listOfCompletableFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> listOfCompletableFutures.stream()
                                                        .map(CompletableFuture::join)
                                                        .collect(Collectors.toList()));
    }
}
