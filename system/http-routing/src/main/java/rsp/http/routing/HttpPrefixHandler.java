package rsp.http.routing;

import rsp.http.HttpRequest;
import rsp.http.HttpResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Asynchronous handler for one method-aware literal path-prefix route. */
@FunctionalInterface
public interface HttpPrefixHandler {
    CompletionStage<HttpResponse> handle(HttpRequest request, HttpPrefixContext route);

    /** Adapts a synchronous prefix handler without hiding failures from the transport. */
    static HttpPrefixHandler sync(Synchronous handler) {
        Objects.requireNonNull(handler, "handler");
        return (request, route) -> {
            try {
                return CompletableFuture.completedFuture(
                        Objects.requireNonNull(handler.handle(request, route), "handler response"));
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        };
    }

    /** Synchronous prefix-handler shape used by {@link #sync(Synchronous)}. */
    @FunctionalInterface
    interface Synchronous {
        HttpResponse handle(HttpRequest request, HttpPrefixContext route);
    }
}
