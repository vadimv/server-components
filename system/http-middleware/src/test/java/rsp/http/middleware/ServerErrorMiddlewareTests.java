package rsp.http.middleware;

import org.junit.jupiter.api.Test;
import rsp.http.HttpApplication;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerErrorMiddlewareTests {
    @Test
    void sanitizes_synchronous_and_asynchronous_failures_and_reports_the_unwrapped_type() {
        List<Throwable> failures = new ArrayList<>();
        ServerErrorMiddleware middleware = new ServerErrorMiddleware((_, failure) -> failures.add(failure));
        HttpApplication synchronous = _ -> { throw new IllegalStateException("private-sync"); };
        HttpApplication asynchronous = _ -> CompletableFuture.failedFuture(
                new IllegalArgumentException("private-async"));

        HttpResponse first = middleware.wrap(synchronous).handle(request()).toCompletableFuture().join();
        HttpResponse second = middleware.wrap(asynchronous).handle(request()).toCompletableFuture().join();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, first.status());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, second.status());
        assertEquals(0, first.body().contentLength().orElseThrow());
        assertEquals(List.of(IllegalStateException.class, IllegalArgumentException.class),
                failures.stream().map(Object::getClass).toList());
    }

    @Test
    void reporter_failures_do_not_replace_the_sanitized_response() {
        ServerErrorMiddleware middleware = new ServerErrorMiddleware((_, _) -> {
            throw new IllegalStateException("reporter");
        });

        HttpResponse response = middleware.wrap(_ -> CompletableFuture.failedFuture(
                        new IllegalStateException("application")))
                .handle(request()).toCompletableFuture().join();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status());
    }

    private static HttpRequest request() {
        return new HttpRequest(HttpMethod.GET, "/failure?secret=hidden", "/failure",
                URI.create("http://localhost/failure?secret=hidden"),
                "http://localhost/failure?secret=hidden", Path.parse("/failure"), Query.EMPTY,
                HttpHeaders.EMPTY, RequestBody.EMPTY);
    }
}
