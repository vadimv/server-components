package rsp.routing;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface Route<T, S> extends Function<T, Optional<CompletableFuture<? extends S>>> {
}
