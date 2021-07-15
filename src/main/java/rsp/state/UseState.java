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

    default boolean isInstanceOf(Class<? extends S> clazz) {
        return clazz.isAssignableFrom(get().getClass());
    }

    default <T extends S> UseState<T> cast(Class<T> clazz) {
        if (this.isInstanceOf(clazz)) {
            return readWrite(() -> (T) get(), v -> accept((T)v));
        } else {
            throw new ClassCastException("Unable cast the underlying type " + get().getClass() + " to " + clazz);
        }
    }

    static <S> UseState<S> readWriteInCompletableFuture(Supplier<S> supplier, CompletableFutureConsumer<S> completableFutureConsumer) {
        return new UseState<S>() {
            @Override
            public void accept(S s) {
                accept(CompletableFuture.completedFuture(s));
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(CompletableFuture<S> cf) {
                completableFutureConsumer.accept(cf);
            }

            @Override
            public void accept(Function<S, S> f) {
                accept(new CompletableFuture<S>().thenApply(f));
            }

            @Override
            public void acceptOptional(Function<S, Optional<S>> function) {
                function.apply(supplier.get()).ifPresent(s -> accept(s));
            }
        };
    }

    static <S> UseState<S> readWriteInFunction(Supplier<S> supplier, FunctionConsumer<S> functionConsumer) {
        return new UseState<S>() {
            @Override
            public void accept(S s) {
                functionConsumer.accept(p -> s);
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(CompletableFuture<S> completableFuture) {
                completableFuture.thenAccept(s -> accept(s));
            }

            @Override
            public void accept(Function<S, S> f) {
                functionConsumer.accept(f);
            }

            @Override
            public void acceptOptional(Function<S, Optional<S>> function) {
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
    static <S> UseState<S> readWrite(Supplier<S> supplier, Consumer<S> consumer) {
        return new UseState<>() {

            @Override
            public void accept(Function<S, S> function) {
                consumer.accept(function.apply(supplier.get()));
            }

            @Override
            public void acceptOptional(Function<S, Optional<S>> function) {
                function.apply(get()).ifPresent(s -> consumer.accept(s));
            }

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(S s) {
                consumer.accept(s);
            }

            @Override
            public void accept(CompletableFuture<S> completableFuture) {
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
    static <S> UseState<S> readOnly(Supplier<S> supplier) {
        return new UseState<>() {

            @Override
            public S get() {
                return supplier.get();
            }

            @Override
            public void accept(S s) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void accept(CompletableFuture<S> completableFuture) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void accept(Function<S, S> function) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
            }

            @Override
            public void acceptOptional(Function<S, Optional<S>> function) {
                throw new IllegalStateException("Not allowed for a read-only UseState instance");
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
            public void accept(Void s) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(CompletableFuture<Void> completableFuture) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void accept(Function<Void, Void> function) {
                throw new IllegalStateException("Not allowed for the Void type");
            }

            @Override
            public void acceptOptional(Function<Void, Optional<Void>> function) {
                throw new IllegalStateException("Not allowed for the Void type");
            }
        };
    }
}
