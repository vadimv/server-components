package rsp.http.json;

import rsp.http.HttpRequest;
import rsp.http.HttpResponse;
import rsp.http.HttpStatus;
import rsp.http.MediaType;
import rsp.util.json.Json;
import rsp.util.json.JsonCodec;
import rsp.util.json.JsonDataType;
import rsp.util.json.JsonDecodingException;
import rsp.util.json.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** HTTP integration helpers for the framework's immutable JSON value tree. */
public final class JsonHttp {
    public static final MediaType JSON_UTF_8 = MediaType.parse("application/json; charset=utf-8");

    private JsonHttp() {
    }

    /** Parses a required JSON request body using the default JSON limits. */
    public static JsonDataType read(HttpRequest request) {
        return read(request, Json.parser());
    }

    /** Parses a required JSON request body using a caller-selected parser/limit profile. */
    public static JsonDataType read(HttpRequest request, JsonParser parser) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(parser, "parser");
        if (request.body().size() == 0) {
            throw new JsonHttpException(HttpStatus.BAD_REQUEST, "A JSON request body is required");
        }
        requireJsonContentType(request.header("Content-Type"));
        try {
            return parser.parse(request.body().text(StandardCharsets.UTF_8));
        } catch (JsonDataType.JsonException failure) {
            throw new JsonHttpException(HttpStatus.BAD_REQUEST, "The JSON request body is malformed", failure);
        }
    }

    /** Parses and decodes a required JSON request body with a domain codec. */
    public static <T> T read(HttpRequest request, JsonCodec<T> codec) {
        return read(request, Json.parser(), codec);
    }

    /** Parses and decodes a required JSON request body with explicit limits and a domain codec. */
    public static <T> T read(HttpRequest request, JsonParser parser, JsonCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        JsonDataType value = read(request, parser);
        try {
            return codec.decode(value);
        } catch (JsonDecodingException failure) {
            throw new JsonHttpException(HttpStatus.BAD_REQUEST,
                    "The JSON request body does not match the expected shape", failure);
        }
    }

    /** Returns a {@code 200 application/json} response. */
    public static HttpResponse response(JsonDataType value) {
        return response(HttpStatus.OK, value);
    }

    /** Serializes one JSON value into a response with an exact UTF-8 content length. */
    public static HttpResponse response(HttpStatus status, JsonDataType value) {
        Objects.requireNonNull(status, "status");
        byte[] bytes = Json.write(Objects.requireNonNull(value, "value")).getBytes(StandardCharsets.UTF_8);
        return HttpResponse.status(status).bytes(bytes, JSON_UTF_8).build();
    }

    /** Encodes one domain value into a {@code 200 application/json} response. */
    public static <T> HttpResponse response(T value, JsonCodec<T> codec) {
        return response(HttpStatus.OK, value, codec);
    }

    /** Encodes one domain value into an application/json response. */
    public static <T> HttpResponse response(HttpStatus status, T value, JsonCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return response(status, codec.encode(value));
    }

    /** Returns a deterministic JSON error envelope. */
    public static HttpResponse error(HttpStatus status, String error, String message) {
        JsonDataType.Object body = Json.object()
                .put("error", Objects.requireNonNull(error, "error"))
                .put("message", Objects.requireNonNull(message, "message"));
        return response(status, body);
    }

    /** Converts a typed JSON request failure into the standard JSON error envelope. */
    public static HttpResponse error(JsonHttpException failure) {
        Objects.requireNonNull(failure, "failure");
        return error(failure.status(), "invalid_json", failure.getMessage());
    }

    private static void requireJsonContentType(String header) {
        if (header == null || header.isBlank()) {
            throw unsupported("A non-empty JSON body requires a Content-Type header");
        }
        String[] parts = header.split(";");
        String type = parts[0].trim().toLowerCase(Locale.ROOT);
        boolean json = type.equals("application/json")
                || type.startsWith("application/") && type.endsWith("+json");
        if (!json) {
            throw unsupported("Unsupported JSON media type: " + type);
        }
        for (int i = 1; i < parts.length; i++) {
            String parameter = parts[i].trim();
            int equals = parameter.indexOf('=');
            if (equals > 0 && parameter.substring(0, equals).trim().equalsIgnoreCase("charset")) {
                String charset = unquote(parameter.substring(equals + 1).trim());
                if (!charset.equalsIgnoreCase(StandardCharsets.UTF_8.name())) {
                    throw unsupported("JSON request charset must be UTF-8");
                }
            }
        }
    }

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1)
                : value;
    }

    private static JsonHttpException unsupported(String message) {
        return new JsonHttpException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}
