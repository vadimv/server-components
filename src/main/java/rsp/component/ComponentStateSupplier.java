package rsp.component;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    CompletableFuture<? extends S> getState(ComponentCompositeKey compositeKey);

}
