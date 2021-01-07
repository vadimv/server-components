package rsp.state;

import java.util.concurrent.CompletableFuture;

public interface CompletableFutureConsumer<S> {
    void accept(CompletableFuture<S> completableFuture);
}
