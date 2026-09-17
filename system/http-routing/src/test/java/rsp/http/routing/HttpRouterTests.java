package rsp.http.routing;

import org.junit.jupiter.api.Test;
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
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRouterTests {
    private static final HttpRouteHandler USERS = HttpRouteHandler.sync((_, route) ->
            HttpResponse.ok().text("user=" + route.requiredParameter("id")).build());

    @Test
    void dispatches_method_and_decoded_path_parameters() {
        HttpRouter router = HttpRouter.builder().get("/users/{id}", USERS).build();

        HttpResponse response = router.handle(request(HttpMethod.GET, "/users/Alice%20Smith")).toCompletableFuture().join();

        assertEquals(HttpStatus.OK, response.status());
        assertEquals("user=Alice Smith", read(response));
    }

    @Test
    void literal_routes_win_independently_of_registration_order() {
        HttpRouter router = HttpRouter.builder()
                .get("/users/{id}", USERS)
                .get("/users/me", HttpRouteHandler.sync((_, _) -> HttpResponse.ok().text("self").build()))
                .build();

        assertEquals("self", read(router.handle(request(HttpMethod.GET, "/users/me")).toCompletableFuture().join()));
    }

    @Test
    void explicit_head_wins_and_head_otherwise_falls_back_to_get() {
        HttpRouter explicit = HttpRouter.builder()
                .get("/health", HttpRouteHandler.sync((_, _) -> HttpResponse.ok().text("get").build()))
                .head("/health", HttpRouteHandler.sync((_, _) -> HttpResponse.ok().header("X-Route", "head").build()))
                .build();
        HttpRouter fallback = HttpRouter.builder()
                .get("/health", HttpRouteHandler.sync((_, _) -> HttpResponse.ok().header("X-Route", "get").build()))
                .build();

        assertEquals("head", explicit.handle(request(HttpMethod.HEAD, "/health")).toCompletableFuture().join()
                .headers().first("X-Route").orElseThrow());
        assertEquals("get", fallback.handle(request(HttpMethod.HEAD, "/health")).toCompletableFuture().join()
                .headers().first("X-Route").orElseThrow());
    }

    @Test
    void returns_not_found_or_method_not_allowed_with_allow_header() {
        HttpRouter router = HttpRouter.builder()
                .get("/items/{id}", USERS)
                .post("/items/{id}", USERS)
                .delete("/items/{id}", USERS)
                .build();

        HttpResponse missing = router.handle(request(HttpMethod.GET, "/missing")).toCompletableFuture().join();
        HttpResponse disallowed = router.handle(request(HttpMethod.PUT, "/items/7")).toCompletableFuture().join();

        assertEquals(HttpStatus.NOT_FOUND, missing.status());
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, disallowed.status());
        assertEquals("DELETE, GET, HEAD, POST", disallowed.headers().first("Allow").orElseThrow());
    }

    @Test
    void dispatches_put_patch_and_options_helpers() {
        HttpRouteHandler method = HttpRouteHandler.sync((request, _) ->
                HttpResponse.ok().text(request.method().name()).build());
        HttpRouter router = HttpRouter.builder()
                .put("/items/{id}", method)
                .patch("/items/{id}", method)
                .options("/items/{id}", method)
                .build();

        assertEquals("PUT", read(router.handle(request(HttpMethod.PUT, "/items/7")).toCompletableFuture().join()));
        assertEquals("PATCH", read(router.handle(request(HttpMethod.PATCH, "/items/7")).toCompletableFuture().join()));
        assertEquals("OPTIONS", read(router.handle(request(HttpMethod.OPTIONS, "/items/7")).toCompletableFuture().join()));
    }

    @Test
    void permits_the_same_template_for_different_methods_but_rejects_per_method_ambiguity() {
        HttpRouter.builder().get("/items/{id}", USERS).post("/items/{id}", USERS).build();

        assertThrows(IllegalArgumentException.class, () -> HttpRouter.builder()
                .get("/items/{id}", USERS)
                .get("/items/{name}", USERS)
                .build());
    }

    @Test
    void converts_synchronous_handler_failures_to_failed_stages() {
        HttpRouter router = HttpRouter.builder()
                .get("/failure", HttpRouteHandler.sync((_, _) -> { throw new IllegalStateException("boom"); }))
                .build();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> router.handle(request(HttpMethod.GET, "/failure")).toCompletableFuture().join());
        assertEquals("boom", failure.getCause().getMessage());
    }

    private static HttpRequest request(HttpMethod method, String rawTarget) {
        String rawPath = rawTarget.contains("?") ? rawTarget.substring(0, rawTarget.indexOf('?')) : rawTarget;
        return new HttpRequest(method, rawTarget, rawPath, URI.create("http://localhost" + rawTarget),
                "http://localhost" + rawTarget, Path.parse(rawPath), Query.EMPTY,
                HttpHeaders.of(new HttpHeader("Host", "localhost")), RequestBody.EMPTY);
    }

    private static String read(HttpResponse response) {
        try (var input = response.body().openStream()) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
