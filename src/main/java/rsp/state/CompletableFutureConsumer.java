package rsp.state;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a write state snapshot operation using a {@link CompletableFuture}.
 * @param <S> the type of the state snapshot, an immutable class
 */
@FunctionalInterface
public interface CompletableFutureConsumer<S> {

    /**
     * Performs this write operation when the argument {@link CompletableFuture} completes with its result
     * @param completableFuture a computation resulting in a write
     */
    void accept(CompletableFuture<S> completableFuture);
}
