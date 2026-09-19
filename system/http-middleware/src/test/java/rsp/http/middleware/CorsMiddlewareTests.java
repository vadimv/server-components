package rsp.http.middleware;

import org.junit.jupiter.api.Test;
import rsp.http.HttpApplication;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsMiddlewareTests {
    private static final String ORIGIN = "https://client.example";

    @Test
    void answers_allowed_preflight_without_invoking_the_application() {
        CorsPolicy policy = CorsPolicy.builder()
                .allowOrigin(ORIGIN)
                .allowMethods(HttpMethod.GET, HttpMethod.POST)
                .allowHeaders("Content-Type", "Authorization")
                .allowCredentials()
                .maxAge(Duration.ofMinutes(10))
                .build();
        AtomicInteger calls = new AtomicInteger();
        HttpApplication application = _ -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(HttpResponse.ok().build());
        };
        HttpHeaders headers = HttpHeaders.builder()
                .add("Origin", ORIGIN)
                .add("Access-Control-Request-Method", "POST")
                .add("Access-Control-Request-Headers", "authorization, Content-Type")
                .build();

        HttpResponse response = new CorsMiddleware(policy).wrap(application)
                .handle(request(HttpMethod.OPTIONS, headers)).toCompletableFuture().join();

        assertEquals(HttpStatus.NO_CONTENT, response.status());
        assertEquals(0, calls.get());
        assertEquals(ORIGIN, response.header("Access-Control-Allow-Origin"));
        assertEquals("true", response.header("Access-Control-Allow-Credentials"));
        assertEquals("authorization, Content-Type", response.header("Access-Control-Allow-Headers"));
        assertEquals("600", response.header("Access-Control-Max-Age"));
        assertEquals("Origin, Access-Control-Request-Method, Access-Control-Request-Headers",
                response.header("Vary"));
    }

    @Test
    void decorates_allowed_actual_responses_and_denies_disallowed_preflight() {
        CorsPolicy policy = CorsPolicy.builder()
                .allowOrigin(ORIGIN)
                .allowMethods(HttpMethod.GET)
                .allowHeaders("Content-Type")
                .exposeHeaders(RequestIdMiddleware.HEADER_NAME)
                .build();
        HttpApplication application = _ -> CompletableFuture.completedFuture(HttpResponse.ok()
                .header("Vary", "Accept-Encoding").build());
        CorsMiddleware middleware = new CorsMiddleware(policy);

        HttpResponse actual = middleware.wrap(application)
                .handle(request(HttpMethod.GET, HttpHeaders.of(new HttpHeader("Origin", ORIGIN))))
                .toCompletableFuture().join();
        HttpHeaders deniedHeaders = HttpHeaders.builder()
                .add("Origin", ORIGIN)
                .add("Access-Control-Request-Method", "DELETE")
                .build();
        HttpResponse denied = middleware.wrap(application)
                .handle(request(HttpMethod.OPTIONS, deniedHeaders)).toCompletableFuture().join();

        assertEquals(ORIGIN, actual.header("Access-Control-Allow-Origin"));
        assertEquals(RequestIdMiddleware.HEADER_NAME, actual.header("Access-Control-Expose-Headers"));
        assertEquals("Accept-Encoding, Origin", actual.header("Vary"));
        assertEquals(HttpStatus.FORBIDDEN, denied.status());
    }

    @Test
    void rejects_an_any_origin_credentials_policy() {
        assertThrows(IllegalStateException.class, () -> CorsPolicy.builder()
                .allowAnyOrigin()
                .allowMethods(HttpMethod.GET)
                .allowCredentials()
                .build());
    }

    private static HttpRequest request(HttpMethod method, HttpHeaders headers) {
        return new HttpRequest(method, "/resource", "/resource", URI.create("http://localhost/resource"),
                "http://localhost/resource", Path.parse("/resource"), Query.EMPTY,
                headers, RequestBody.EMPTY);
    }
}
