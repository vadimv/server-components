package rsp.http.middleware;

import org.junit.jupiter.api.Test;
import rsp.http.HttpApplication;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestIdMiddlewareTests {
    @Test
    void preserves_one_safe_identifier_for_the_handler_and_response() {
        HttpApplication terminal = request -> CompletableFuture.completedFuture(HttpResponse.ok()
                .header("Observed-ID", request.header(RequestIdMiddleware.HEADER_NAME)).build());
        HttpRequest request = request(HttpHeaders.of(new HttpHeader(RequestIdMiddleware.HEADER_NAME, "edge-123")));

        HttpResponse response = new RequestIdMiddleware(() -> "generated").wrap(terminal)
                .handle(request).toCompletableFuture().join();

        assertEquals("edge-123", response.header("Observed-ID"));
        assertEquals("edge-123", response.header(RequestIdMiddleware.HEADER_NAME));
    }

    @Test
    void replaces_unsafe_or_ambiguous_identifiers() {
        HttpHeaders headers = HttpHeaders.builder()
                .add(RequestIdMiddleware.HEADER_NAME, "one")
                .add(RequestIdMiddleware.HEADER_NAME, "two")
                .build();
        HttpApplication terminal = request -> CompletableFuture.completedFuture(HttpResponse.ok()
                .header("Observed-ID", request.header(RequestIdMiddleware.HEADER_NAME)).build());

        HttpResponse response = new RequestIdMiddleware(() -> "generated-456").wrap(terminal)
                .handle(request(headers)).toCompletableFuture().join();

        assertEquals("generated-456", response.header("Observed-ID"));
        assertEquals("generated-456", response.header(RequestIdMiddleware.HEADER_NAME));
    }

    private static HttpRequest request(HttpHeaders headers) {
        return new HttpRequest(HttpMethod.GET, "/items?secret=hidden", "/items",
                URI.create("http://localhost/items?secret=hidden"),
                "http://localhost/items?secret=hidden", Path.parse("/items"), Query.EMPTY,
                headers, RequestBody.EMPTY);
    }
}
