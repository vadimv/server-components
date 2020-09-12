package rsp;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.*;
import java.util.stream.*;

public class SingleElementStream<T> implements Stream<T> {
    private final T value;

    public SingleElementStream(T value) {
        this.value = value;
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return null;
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> function) {
        return null;
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> toIntFunction) {
        return null;
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> toLongFunction) {
        return null;
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> toDoubleFunction) {
        return null;
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> function) {
        return null;
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> function) {
        return null;
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> function) {
        return null;
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> function) {
        return null;
    }

    @Override
    public Stream<T> distinct() {
        return null;
    }

    @Override
    public Stream<T> sorted() {
        return null;
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return null;
    }

    @Override
    public Stream<T> peek(Consumer<? super T> consumer) {
        return null;
    }

    @Override
    public Stream<T> limit(long l) {
        return null;
    }

    @Override
    public Stream<T> skip(long l) {
        return null;
    }

    @Override
    public void forEach(Consumer<? super T> consumer) {

    }

    @Override
    public void forEachOrdered(Consumer<? super T> consumer) {

    }

    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> intFunction) {
        return null;
    }

    @Override
    public T reduce(T t, BinaryOperator<T> binaryOperator) {
        return null;
    }

    @Override
    public Optional<T> reduce(BinaryOperator<T> binaryOperator) {
        return Optional.empty();
    }

    @Override
    public <U> U reduce(U u, BiFunction<U, ? super T, U> biFunction, BinaryOperator<U> binaryOperator) {
        return null;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> biConsumer, BiConsumer<R, R> biConsumer1) {
        return null;
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return null;
    }

    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return Optional.empty();
    }

    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return Optional.empty();
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return false;
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return false;
    }

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return false;
    }

    @Override
    public Optional<T> findFirst() {
        return Optional.empty();
    }

    @Override
    public Optional<T> findAny() {
        return Optional.empty();
    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }

    @Override
    public Spliterator<T> spliterator() {
        return null;
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @Override
    public Stream<T> sequential() {
        return null;
    }

    @Override
    public Stream<T> parallel() {
        return null;
    }

    @Override
    public Stream<T> unordered() {
        return null;
    }

    @Override
    public Stream<T> onClose(Runnable runnable) {
        return null;
    }

    @Override
    public void close() {

    }
}
