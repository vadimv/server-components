package rsp.http.middleware;

import org.junit.jupiter.api.Test;
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

class SecurityHeadersMiddlewareTests {
    @Test
    void baseline_overwrites_weaker_application_values() {
        HttpResponse response = SecurityHeadersMiddleware.defaults()
                .wrap(_ -> CompletableFuture.completedFuture(HttpResponse.ok()
                        .header("X-Frame-Options", "SAMEORIGIN").build()))
                .handle(request()).toCompletableFuture().join();

        assertEquals("nosniff", response.header("X-Content-Type-Options"));
        assertEquals("DENY", response.header("X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", response.header("Referrer-Policy"));
    }

    private static HttpRequest request() {
        return new HttpRequest(HttpMethod.GET, "/", "/", URI.create("http://localhost/"),
                "http://localhost/", Path.parse("/"), Query.EMPTY,
                HttpHeaders.EMPTY, RequestBody.EMPTY);
    }
}
