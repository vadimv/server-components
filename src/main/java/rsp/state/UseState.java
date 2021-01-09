package rsp.state;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Represents a state snapshot read, write and write using a {@link CompletableFuture} access.
 * @param <S> the type of the state snapshot, an immutable class
 */
public interface UseState<S> extends Supplier<S>, Consumer<S>, CompletableFutureConsumer<S> {

    /**
     * Creates a new {@link UseState} wrapper instance.
     * @param supplier a supplier of a state snapshot for a read operation
     * @param consumer a consumer of a state snapshot for a write operation
     * @param <S> the type of the state snapshot, an immutable class
     * @return a read-write wrapper instance
     */
    static <S> UseState<S> useState(Supplier<S> supplier, Consumer<S> consumer) {
        return new UseState<>() {

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
    static <S> UseState<S> useState(Supplier<S> supplier) {
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
        };
    }

    /**
     * Creates a new {@link UseState} wrapper instance with no specific state type.
     * Throws an exception on a read, write or write using {@link CompletableFuture} attempt.
     * @return a void state wrapper instance
     */
    static UseState<Void> useState() {
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
        };
    }
}
