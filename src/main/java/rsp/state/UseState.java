package rsp.state;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Represents a state snapshot read access as well as
 * methods to write to it.
 * @param <S> the type of the state snapshot, an immutable class
 */
public interface UseState<S> extends Supplier<S>, Consumer<S>, CompletableFutureConsumer<S>, FunctionConsumer<S> {


    static <S> UseState<S> readWriteInCompletableFuture(final Supplier<S> supplier, final CompletableFutureConsumer<S> completableFutureConsumer) {
        return new UseState<>() {
            @Override
            public void accept(final S s) {
                accept(CompletableFuture.completedFuture(s));
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(final CompletableFuture<S> cf) {
                completableFutureConsumer.accept(cf);
            }

            @Override
            public void accept(final Function<S, S> f) {
                accept(new CompletableFuture<S>().thenApply(f));
            }

            @Override
            public void acceptOptional(final Function<S, Optional<S>> function) {
                function.apply(supplier.get()).ifPresent(s -> accept(s));
            }
        };
    }

    static <S> UseState<S> readWriteInFunction(final Supplier<S> supplier, final FunctionConsumer<S> functionConsumer) {
        return new UseState<>() {
            @Override
            public void accept(final S s) {
                functionConsumer.accept(p -> s);
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(final CompletableFuture<S> completableFuture) {
                completableFuture.thenAccept(s -> accept(s));
            }

            @Override
            public void accept(final Function<S, S> f) {
                functionConsumer.accept(f);
            }

            @Override
            public void acceptOptional(final Function<S, Optional<S>> function) {
                function.apply(get()).ifPresent(s -> functionConsumer.accept(p -> s));
            }


        };
    }

    /**
     * Creates a new {@link UseState} wrapper instance.
     * @param supplier a supplier of a state snapshot for a read operation
     * @param consumer a consumer of a state snapshot for a write operation
     * @param <S> the type of the state snapshot, an immutable class
     * @return a read-write wrapper instance
     */
    static <S> UseState<S> readWrite(final Supplier<S> supplier, final Consumer<S> consumer) {
        return new UseState<>() {

            @Override
            public void accept(final Function<S, S> function) {
                consumer.accept(function.apply(supplier.get()));
            }

            @Override
            public void acceptOptional(final Function<S, Optional<S>> function) {
                function.apply(get()).ifPresent(s -> consumer.accept(s));
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(final S s) {
                consumer.accept(s);
            }

            @Override
            public void accept(final CompletableFuture<S> completableFuture) {
                completableFuture.thenAccept(s -> consumer.accept(s));
            }
        };
    }

    /**
     * Creates a new read-only {@link UseState} wrapper instance.
     * Throws an exception on a write or write using {@link CompletableFuture} attempt.
     * @param supplier a supplier of a state snapshot for a read operation
     * @param <S> the type of the state snapshot, an immutable class
     * @return a read-only wrapper instance
     */
    static <S> UseState<S> readOnly(final Supplier<S> supplier) {
        return new UseState<>() {

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(final S s) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void accept(final CompletableFuture<S> completableFuture) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void accept(final Function<S, S> function) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void acceptOptional(final Function<S, Optional<S>> function) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }
        };
    }

    /**
     * Creates a new write-only {@link UseState} wrapper instance.
     * @param consumer a consumer of a state snapshot for a write operation
     * @param <S> the type of the state snapshot, an immutable class
     * @return a read-write wrapper instance
     */
    static <S> UseState<S> writeOnly(final Consumer<S> consumer) {
        return new UseState<>() {

            @Override
            public void accept(final Function<S, S> function) {
                throw new IllegalStateException("Not allowed for a write-only UseState instance");
            }

            @Override
            public void acceptOptional(final Function<S, Optional<S>> function) {
                function.apply(get()).ifPresent(s -> consumer.accept(s));
            }

            @Override
            public S get() {
                throw new IllegalStateException("Not allowed for a write-only UseState instance");
            }

            @Override
            public void accept(final S s) {
                consumer.accept(s);
            }

            @Override
            public void accept(final CompletableFuture<S> completableFuture) {
                completableFuture.thenAccept(s -> consumer.accept(s));
            }
        };
    }

    /**
     * Creates a new {@link UseState} wrapper instance with no specific state type.
     * Throws an exception on a read, write or write using {@link CompletableFuture} attempt.
     * @return a void state wrapper instance
     */
    static UseState<Void> empty() {
        return new UseState<>() {

            @Override
            public Void get() {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(final Void s) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(final CompletableFuture<Void> completableFuture) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(final Function<Void, Void> function) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void acceptOptional(final Function<Void, Optional<Void>> function) {
                throw new IllegalStateException("Not allowed for the Void type");
            }
        };
    }
}
