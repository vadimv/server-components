package rsp.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompletableFutureUtils {

    public static <T> BiConsumer<? super T, ? super Throwable> complete(Consumer<? super T> whenResult,
                                                                        Consumer<? super Throwable> whenException) {
        return new BiConsumer<>() {
            @Override
            public void accept(T result, Throwable ex) {
                if (result != null) {
                    whenResult.accept(result);
                } else {
                    whenException.accept(ex);
                }
            }
        };
    }

    public static <T> CompletableFuture<T> either(CompletableFuture<T>... cfs) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture.allOf(cfs).whenComplete((v, ex) -> {
            boolean allCompletedExceptionally = true;
            for (CompletableFuture<T> cf : cfs) {
                allCompletedExceptionally &= cf.isCompletedExceptionally();
            }
            if (allCompletedExceptionally) {
                result.completeExceptionally(ex);
            }
        });
        if (!result.isCompletedExceptionally()) {
            for (CompletableFuture<T> f : cfs) {
                f.thenAccept(result::complete);
            }
        }
        return result;
    }
}
