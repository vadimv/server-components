package rsp.http;

import java.util.Objects;

/** A single HTTP header field line. */
public record HttpHeader(String name, String value) {
    public HttpHeader {
        name = Objects.requireNonNull(name, "name");
        value = Objects.requireNonNull(value, "value");
        if (name.isBlank() || !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
            throw new IllegalArgumentException("Invalid HTTP header name: " + name);
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("HTTP header value cannot contain a line break");
        }
    }
}
