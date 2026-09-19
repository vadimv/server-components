package rsp.http.json;

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
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonLimits;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonHttpTests {
    private static final JsonCodec<Message> MESSAGE_CODEC = JsonCodec.of(
            value -> new Message(Json.requireObject(value).requiredString("message")),
            message -> Json.object().put("message", message.value()));

    @Test
    void reads_utf8_json_and_vendor_json_media_types() {
        JsonDataType.Object parsed = assertInstanceOf(JsonDataType.Object.class,
                JsonHttp.read(request("application/problem+json; charset=\"UTF-8\"", "{\"name\":\"Zażółć\"}")));

        assertEquals(new JsonDataType.String("Zażółć"), parsed.value("name"));
    }

    @Test
    void rejects_empty_malformed_unsupported_and_non_utf8_bodies_with_typed_statuses() {
        JsonHttpException empty = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("application/json", "")));
        JsonHttpException malformed = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("application/json", "{")));
        JsonHttpException unsupported = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("text/plain", "{}")));
        JsonHttpException missing = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request(null, "{}")));
        JsonHttpException charset = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("application/json; charset=iso-8859-1", "{}")));

        assertEquals(HttpStatus.BAD_REQUEST, empty.status());
        assertEquals(HttpStatus.BAD_REQUEST, malformed.status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, missing.status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, charset.status());
    }

    @Test
    void honors_a_caller_selected_json_limit_profile() {
        JsonLimits limits = new JsonLimits(1, 10, 10, 1, 1, 3, 20);

        JsonHttpException failure = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("application/json", "{\"long\":\"value\"}"), Json.parser(limits)));

        assertEquals(HttpStatus.BAD_REQUEST, failure.status());
    }

    @Test
    void writes_exact_utf8_json_responses_and_error_envelopes() throws Exception {
        HttpResponse response = JsonHttp.response(HttpStatus.CREATED,
                new JsonDataType.Object().put("message", new JsonDataType.String("cześć")));
        byte[] bytes = response.body().openStream().readAllBytes();

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("application/json; charset=utf-8", response.headers().first("Content-Type").orElseThrow());
        assertEquals(response.body().contentLength().orElseThrow(), bytes.length);
        assertEquals(new JsonDataType.String("cześć"),
                assertInstanceOf(JsonDataType.Object.class,
                        Json.parse(new String(bytes, StandardCharsets.UTF_8))).value("message"));

        HttpResponse error = JsonHttp.error(new JsonHttpException(HttpStatus.BAD_REQUEST, "bad input"));
        assertEquals("{\"error\":\"invalid_json\",\"message\":\"bad input\"}", read(error));
    }

    @Test
    void decodes_and_encodes_domain_values_with_a_codec() throws Exception {
        Message decoded = JsonHttp.read(request("application/json", "{\"message\":\"hello\"}"),
                MESSAGE_CODEC);
        HttpResponse response = JsonHttp.response(HttpStatus.CREATED, decoded, MESSAGE_CODEC);

        assertEquals(new Message("hello"), decoded);
        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("{\"message\":\"hello\"}", read(response));

        JsonHttpException wrongShape = assertThrows(JsonHttpException.class,
                () -> JsonHttp.read(request("application/json", "{\"message\":7}"), MESSAGE_CODEC));
        assertEquals(HttpStatus.BAD_REQUEST, wrongShape.status());
        assertEquals("The JSON request body does not match the expected shape", wrongShape.getMessage());
    }

    private static HttpRequest request(String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new HttpRequest(HttpMethod.POST, "/items", "/items", URI.create("http://localhost/items"),
                "http://localhost/items", Path.of("/items"), Query.EMPTY,
                contentType == null ? HttpHeaders.EMPTY : HttpHeaders.of(new HttpHeader("Content-Type", contentType)),
                RequestBody.of(bytes, 1024));
    }

    private static String read(HttpResponse response) throws Exception {
        return new String(response.body().openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private record Message(String value) {
    }
}
