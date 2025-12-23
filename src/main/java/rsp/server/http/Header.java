package rsp.server.http;

import java.util.Objects;

/**
 * Represents an HTTP header.
 * @see HttpRequest
 * @see HttpResponse
 * @param name
 * @param value
 */
public record Header(String name, String value) {
    public Header {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
    }
}
