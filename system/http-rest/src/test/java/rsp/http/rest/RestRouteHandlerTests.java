package rsp.http.rest;

import org.junit.jupiter.api.Test;
import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.RequestBody;
import rsp.http.json.JsonHttp;
import rsp.http.routing.HttpRouter;
import rsp.url.Path;
import rsp.url.Query;
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;
import rsp.util.json.JsonDataType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestRouteHandlerTests {
    private static final JsonCodec<Message> MESSAGE_CODEC = JsonCodec.of(
            value -> new Message(Json.requireObject(value).requiredString("message")),
            message -> Json.object().put("message", message.value()));

    @Test
    void parses_typed_json_bodies_and_encodes_domain_responses() {
        HttpRouter router = HttpRouter.builder()
                .post("/messages", RestRouteHandler.json(MESSAGE_CODEC, (_, _, message) ->
                        CompletableFuture.completedFuture(JsonHttp.response(HttpStatus.CREATED,
                                new Message(message.value().toUpperCase()), MESSAGE_CODEC))))
                .build();

        HttpResponse response = router.handle(request(HttpMethod.POST, "/messages",
                        "application/json", "{\"message\":\"hello\"}"))
                .toCompletableFuture().join();

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("{\"message\":\"HELLO\"}", read(response));
    }

    @Test
    void maps_json_syntax_media_type_and_shape_failures_without_invoking_the_endpoint() {
        AtomicInteger calls = new AtomicInteger();
        HttpRouter router = HttpRouter.builder()
                .post("/messages", RestRouteHandler.jsonSync(MESSAGE_CODEC, (_, _, message) -> {
                    calls.incrementAndGet();
                    return JsonHttp.response(message, MESSAGE_CODEC);
                }))
                .build();

        HttpResponse malformed = router.handle(request(HttpMethod.POST, "/messages",
                "application/json", "{")).toCompletableFuture().join();
        HttpResponse unsupported = router.handle(request(HttpMethod.POST, "/messages",
                "text/plain", "{}")).toCompletableFuture().join();
        HttpResponse wrongShape = router.handle(request(HttpMethod.POST, "/messages",
                "application/json", "{\"message\":1}")).toCompletableFuture().join();

        assertEquals(HttpStatus.BAD_REQUEST, malformed.status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.status());
        assertEquals(HttpStatus.BAD_REQUEST, wrongShape.status());
        assertEquals("invalid_json", errorCode(malformed));
        assertEquals("invalid_json", errorCode(unsupported));
        assertEquals("invalid_json", errorCode(wrongShape));
        assertEquals(0, calls.get());
    }

    @Test
    void maps_expected_synchronous_and_asynchronous_rest_failures() {
        HttpRouter router = HttpRouter.builder()
                .get("/items/{id}", RestRouteHandler.sync((_, route) -> {
                    throw RestException.notFound("item_not_found",
                            "Item " + route.requiredParameter("id") + " was not found");
                }))
                .post("/items", RestRouteHandler.async((_, _) ->
                        CompletableFuture.failedFuture(new CompletionException(
                                RestException.conflict("duplicate_item", "The item already exists")))))
                .build();

        HttpResponse missing = router.handle(request(HttpMethod.GET, "/items/42", null, ""))
                .toCompletableFuture().join();
        HttpResponse conflict = router.handle(request(HttpMethod.POST, "/items", null, ""))
                .toCompletableFuture().join();

        assertEquals(HttpStatus.NOT_FOUND, missing.status());
        assertEquals("{\"error\":\"item_not_found\",\"message\":\"Item 42 was not found\"}",
                read(missing));
        assertEquals(HttpStatus.CONFLICT, conflict.status());
        assertEquals("duplicate_item", errorCode(conflict));
    }

    @Test
    void preserves_unexpected_failures_for_the_transport_boundary() {
        RestRouteHandler handler = RestRouteHandler.async((_, _) ->
                CompletableFuture.failedFuture(new IllegalStateException("boom")));

        CompletionException failure = assertThrows(CompletionException.class,
                () -> handler.handle(request(HttpMethod.GET, "/failure", null, ""), null)
                        .toCompletableFuture().join());

        assertEquals(IllegalStateException.class, failure.getCause().getClass());
        assertEquals("boom", failure.getCause().getMessage());
    }

    @Test
    void raw_json_handler_keeps_the_value_tree_available() {
        RestRouteHandler handler = RestRouteHandler.jsonSync((_, _, body) ->
                JsonHttp.response(Json.object().put("array", body instanceof JsonDataType.Array)));

        HttpResponse response = handler.handle(request(HttpMethod.POST, "/raw",
                        "application/json", "[1,2]"), null)
                .toCompletableFuture().join();

        assertEquals("{\"array\":true}", read(response));
    }

    @Test
    void rest_failures_require_an_error_status_and_stable_code() {
        assertThrows(IllegalArgumentException.class,
                () -> new RestException(HttpStatus.OK, "not_an_error", "invalid status"));
        assertThrows(IllegalArgumentException.class,
                () -> RestException.badRequest(" ", "invalid code"));
    }

    private static HttpRequest request(HttpMethod method, String path, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = contentType == null
                ? HttpHeaders.EMPTY
                : HttpHeaders.of(new HttpHeader("Content-Type", contentType));
        return new HttpRequest(method, path, path, URI.create("http://localhost" + path),
                "http://localhost" + path, Path.parse(path), Query.EMPTY, headers,
                RequestBody.of(bytes, 1024));
    }

    private static String errorCode(HttpResponse response) {
        JsonDataType.Object body = (JsonDataType.Object) Json.parse(read(response));
        return body.requiredString("error");
    }

    private static String read(HttpResponse response) {
        try (var input = response.body().openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private record Message(String value) {
    }
}
