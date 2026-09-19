package rsp.http;

import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Supplier;

/** Immutable HTTP response with a fluent construction DSL. */
public record HttpResponse(HttpStatus status, HttpHeaders headers, ResponseBody body) implements HttpResult {
    public HttpResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
    }

    public static Builder status(HttpStatus status) {
        return new Builder(status);
    }

    public static Builder ok() {
        return status(HttpStatus.OK);
    }

    public static HttpResponse redirect(URI location) {
        return status(HttpStatus.FOUND).header("Location", Objects.requireNonNull(location, "location").toASCIIString()).build();
    }

    public String header(String name) {
        return headers.first(name).orElse(null);
    }

    /** Returns a copy with all values for {@code name} replaced by one value. */
    public HttpResponse withHeader(String name, String value) {
        HttpHeaders replaced = HttpHeaders.builder()
                .addAll(headers)
                .set(name, value)
                .build();
        return new HttpResponse(status, replaced, body);
    }

    /** Returns a copy with an additional header value. */
    public HttpResponse addHeader(String name, String value) {
        HttpHeaders extended = HttpHeaders.builder()
                .addAll(headers)
                .add(name, value)
                .build();
        return new HttpResponse(status, extended, body);
    }

    public static final class Builder {
        private final HttpStatus status;
        private final HttpHeaders.Builder headers = HttpHeaders.builder();
        private ResponseBody body = ResponseBody.EMPTY;

        private Builder(HttpStatus status) {
            this.status = Objects.requireNonNull(status, "status");
        }

        public Builder header(String name, String value) {
            headers.add(name, value);
            return this;
        }

        public Builder setHeader(String name, String value) {
            headers.set(name, value);
            return this;
        }

        public Builder cookie(SetCookie cookie) {
            return header("Set-Cookie", Objects.requireNonNull(cookie, "cookie").headerValue());
        }

        public Builder body(ResponseBody value) {
            body = Objects.requireNonNull(value, "body");
            return this;
        }

        public Builder bytes(byte[] value, MediaType mediaType) {
            return setHeader("Content-Type", mediaType.toString()).body(ResponseBody.bytes(value));
        }

        public Builder text(String value) {
            return text(value, MediaType.TEXT_UTF_8);
        }

        public Builder text(String value, MediaType mediaType) {
            return setHeader("Content-Type", mediaType.toString()).body(ResponseBody.text(value));
        }

        public Builder stream(Supplier<? extends InputStream> streams, OptionalLong length, MediaType mediaType) {
            return setHeader("Content-Type", mediaType.toString()).body(ResponseBody.stream(streams, length));
        }

        public HttpResponse build() {
            return new HttpResponse(status, headers.build(), body);
        }
    }
}
