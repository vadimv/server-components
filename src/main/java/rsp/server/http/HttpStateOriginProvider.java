package rsp.server.http;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class HttpStateOriginProvider<T, S> {
    private final HttpStateOriginLookup lookup;
    private final Class<T> clazz;
    private final Function<T, CompletableFuture<? extends S>> resolveStateFunction;

    public HttpStateOriginProvider(final HttpStateOriginLookup lookup,
                                   final Class<T> clazz,
                                   final Function<T, CompletableFuture<? extends S>> resolveStateFunction) {
        this.lookup = Objects.requireNonNull(lookup);
        this.clazz = Objects.requireNonNull(clazz);
        this.resolveStateFunction = Objects.requireNonNull(resolveStateFunction);
    }

    public CompletableFuture<? extends S> getStatePromise() {
        return resolveStateFunction.apply(lookup.lookup(clazz));
    }

    public void setRelativeUrl(RelativeUrl relativeUrl) {
        lookup.setRelativeUrl(relativeUrl);
    }

    public RelativeUrl relativeUrl() {
        return lookup.relativeUrl();
    }
}
