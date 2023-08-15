package rsp.util;

import java.util.concurrent.CompletableFuture;

public class CompletableFutureUtils {

    private CompletableFutureUtils() {}

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
