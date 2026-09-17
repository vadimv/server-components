package rsp.http.routing;

import rsp.http.HttpRequest;
import rsp.http.HttpResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Asynchronous handler for one method-aware HTTP route. */
@FunctionalInterface
public interface HttpRouteHandler {
    CompletionStage<HttpResponse> handle(HttpRequest request, HttpRouteContext route);

    /** Adapts a synchronous handler without hiding failures from the transport. */
    static HttpRouteHandler sync(Synchronous handler) {
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

    /** Synchronous route-handler shape used by {@link #sync(Synchronous)}. */
    @FunctionalInterface
    interface Synchronous {
        HttpResponse handle(HttpRequest request, HttpRouteContext route);
    }
}
