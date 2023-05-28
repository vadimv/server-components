package rsp.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CompletableFutureUtils {

    public static <T> BiConsumer<? super T, ? super Throwable> consume(final Consumer<? super T> whenResult,
                                                                       final Consumer<? super Throwable> whenException) {
        return new BiConsumer<>() {
            @Override
            public void accept(final T result, final Throwable ex) {
                if (ex != null) {
                    whenException.accept(ex);
                } else if (result == null) {
                    whenException.accept(new NullPointerException("Completion result is null"));
                } else {
                    whenResult.accept(result);
                }
            }
        };
    }

    @SafeVarargs
    public static <T> CompletableFuture<T> either(final CompletableFuture<T>... cfs) {
        final CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture.allOf(cfs).whenComplete((v, ex) -> {
            boolean allCompletedExceptionally = true;
            for (final CompletableFuture<T> cf : cfs) {
                allCompletedExceptionally &= cf.isCompletedExceptionally();
            }
            if (allCompletedExceptionally) {
                result.completeExceptionally(ex);
            }
        });
        if (!result.isCompletedExceptionally()) {
            for (final CompletableFuture<T> f : cfs) {
                f.thenAccept(result::complete);
            }
        }
        return result;
    }
}
