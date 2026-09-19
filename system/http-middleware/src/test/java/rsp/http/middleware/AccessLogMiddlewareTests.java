package rsp.http.middleware;

import org.junit.jupiter.api.Test;
import rsp.http.HttpApplication;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessLogMiddlewareTests {
    @Test
    void emits_bounded_query_free_completion_data() {
        List<HttpAccessEvent> events = new ArrayList<>();
        AtomicLong nanos = new AtomicLong(10);
        AccessLogMiddleware middleware = new AccessLogMiddleware(events::add,
                Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC),
                () -> nanos.getAndAdd(25));
        HttpApplication application = request -> CompletableFuture.completedFuture(
                HttpResponse.ok().build());

        new RequestIdMiddleware(() -> "req-7").wrap(middleware.wrap(application))
                .handle(request()).toCompletableFuture().join();

        HttpAccessEvent event = events.getFirst();
        assertEquals("/items", event.rawPath());
        assertEquals(200, event.statusCode());
        assertEquals(java.time.Duration.ofNanos(25), event.duration());
        assertEquals("req-7", event.requestId().orElseThrow());
        assertEquals(java.util.Optional.empty(), event.failureType());
    }

    @Test
    void records_exception_types_and_sink_failures_do_not_replace_results() {
        List<HttpAccessEvent> events = new ArrayList<>();
        AccessLogMiddleware recording = new AccessLogMiddleware(events::add);
        HttpApplication failed = _ -> CompletableFuture.failedFuture(new IllegalStateException("private"));

        assertThrows(CompletionException.class,
                () -> recording.wrap(failed).handle(request()).toCompletableFuture().join());
        assertEquals(500, events.getFirst().statusCode());
        assertEquals(IllegalStateException.class.getName(), events.getFirst().failureType().orElseThrow());

        AccessLogMiddleware brokenSink = new AccessLogMiddleware(_ -> { throw new IllegalStateException("sink"); });
        assertEquals(200, brokenSink.wrap(_ -> CompletableFuture.completedFuture(HttpResponse.ok().build()))
                .handle(request()).toCompletableFuture().join().status().code());
    }

    private static HttpRequest request() {
        return new HttpRequest(HttpMethod.GET, "/items?secret=hidden", "/items",
                URI.create("http://localhost/items?secret=hidden"),
                "http://localhost/items?secret=hidden", Path.parse("/items"), Query.EMPTY,
                HttpHeaders.EMPTY, RequestBody.EMPTY);
    }
}
