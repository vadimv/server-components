package rsp.component;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    S getState(ComponentCompositeKey compositeKey);

}
