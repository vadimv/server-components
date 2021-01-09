package rsp.state;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a write state snapshot operation using a {@link CompletableFuture}.
 * @param <S> the type of the state snapshot, an immutable class
 */
public interface CompletableFutureConsumer<S> {
    void accept(CompletableFuture<S> completableFuture);
}
