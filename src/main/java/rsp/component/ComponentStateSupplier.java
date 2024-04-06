package rsp.component;

import rsp.server.http.HttpStateOrigin;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ComponentStateSupplier<S> {

    CompletableFuture<? extends S> getState(ComponentCompositeKey compositeKey, HttpStateOrigin httpStateOrigin);

}
