package rsp.app.rest;

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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestHelloTests {
    @Test
    void serves_path_parameters_and_typed_json_bodies() {
        HttpResponse hello = RestHello.application().handle(request(HttpMethod.GET,
                        "/api/hello/Alice", null, ""))
                .toCompletableFuture().join();
        HttpResponse echo = RestHello.application().handle(request(HttpMethod.POST,
                        "/api/echo", "application/json", "{\"message\":\"hello\"}"))
                .toCompletableFuture().join();

        assertEquals(HttpStatus.OK, hello.status());
        assertEquals("{\"message\":\"Hello, Alice\"}", read(hello));
        assertEquals(HttpStatus.CREATED, echo.status());
        assertEquals("{\"message\":\"hello\"}", read(echo));
    }

    @Test
    void maps_an_invalid_typed_body_to_the_standard_error_envelope() {
        HttpResponse response = RestHello.application().handle(request(HttpMethod.POST,
                        "/api/echo", "application/json", "{\"message\":7}"))
                .toCompletableFuture().join();

        assertEquals(HttpStatus.BAD_REQUEST, response.status());
        assertEquals("{\"error\":\"invalid_json\","
                + "\"message\":\"The JSON request body does not match the expected shape\"}",
                read(response));
    }

    @Test
    void serves_generated_openapi_and_cross_cutting_headers() {
        HttpResponse response = RestHello.application().handle(request(HttpMethod.GET,
                        "/openapi.json", null, ""))
                .toCompletableFuture().join();

        assertEquals(HttpStatus.OK, response.status());
        assertEquals("nosniff", response.header("X-Content-Type-Options"));
        org.junit.jupiter.api.Assertions.assertNotNull(response.header("X-Request-ID"));
        String document = read(response);
        org.junit.jupiter.api.Assertions.assertTrue(document.contains("\"openapi\":\"3.1.0\""));
        org.junit.jupiter.api.Assertions.assertTrue(document.contains("\"operationId\":\"hello\""));
        org.junit.jupiter.api.Assertions.assertTrue(document.contains("\"operationId\":\"echoMessage\""));
    }

    private static HttpRequest request(HttpMethod method, String target, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = contentType == null
                ? HttpHeaders.EMPTY
                : HttpHeaders.of(new HttpHeader("Content-Type", contentType));
        return new HttpRequest(method, target, target, URI.create("http://localhost" + target),
                "http://localhost" + target, Path.of(target), Query.EMPTY, headers,
                RequestBody.of(bytes, 1024));
    }

    private static String read(HttpResponse response) {
        try (var input = response.body().openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
