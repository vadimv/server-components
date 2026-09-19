package rsp.http;

import org.junit.jupiter.api.Test;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMiddlewareTests {
    @Test
    void pipeline_enters_in_declaration_order_and_observes_responses_in_reverse() {
        List<String> calls = new ArrayList<>();
        HttpMiddleware first = recording("first", calls);
        HttpMiddleware second = recording("second", calls);
        HttpApplication terminal = request -> {
            calls.add("application");
            return CompletableFuture.completedFuture(HttpResponse.ok().build());
        };

        HttpMiddleware.pipeline(terminal, first, second).handle(request()).toCompletableFuture().join();

        assertEquals(List.of("first-in", "second-in", "application", "second-out", "first-out"), calls);
    }

    @Test
    void pipeline_preserves_middleware_and_application_lifecycle_order() {
        List<String> calls = new ArrayList<>();
        HttpMiddleware middleware = new HttpMiddleware() {
            @Override
            public java.util.concurrent.CompletionStage<HttpResponse> handle(HttpRequest request,
                                                                             HttpApplication next) {
                return next.handle(request);
            }

            @Override
            public void start() {
                calls.add("middleware-start");
            }

            @Override
            public void stop() {
                calls.add("middleware-stop");
            }
        };
        HttpApplication application = new HttpApplication() {
            @Override
            public java.util.concurrent.CompletionStage<HttpResponse> handle(HttpRequest request) {
                return CompletableFuture.completedFuture(HttpResponse.ok().build());
            }

            @Override
            public void start() {
                calls.add("application-start");
            }

            @Override
            public void stop() {
                calls.add("application-stop");
            }
        };

        HttpApplication pipeline = middleware.wrap(application);
        pipeline.start();
        pipeline.stop();

        assertEquals(List.of("middleware-start", "application-start",
                "application-stop", "middleware-stop"), calls);
    }

    @Test
    void synchronous_middleware_failures_become_failed_stages() {
        HttpMiddleware failure = (_, _) -> {
            throw new IllegalStateException("boom");
        };
        HttpApplication pipeline = failure.wrap(_ -> CompletableFuture.completedFuture(HttpResponse.ok().build()));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> pipeline.handle(request()).toCompletableFuture().join());

        assertEquals("boom", thrown.getCause().getMessage());
    }

    private static HttpMiddleware recording(String name, List<String> calls) {
        return (request, next) -> {
            calls.add(name + "-in");
            return next.handle(request).thenApply(response -> {
                calls.add(name + "-out");
                return response;
            });
        };
    }

    private static HttpRequest request() {
        return new HttpRequest(HttpMethod.GET, "/", "/", URI.create("http://localhost/"),
                "http://localhost/", Path.parse("/"), Query.EMPTY, HttpHeaders.EMPTY, RequestBody.EMPTY);
    }
}
