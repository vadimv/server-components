package rsp.http;

import java.util.Objects;

/** A request cookie name/value pair. */
public record Cookie(String name, String value) {
    public Cookie {
        name = Objects.requireNonNull(name, "name");
        value = Objects.requireNonNull(value, "value");
        if (name.isBlank() || name.indexOf('=') >= 0 || name.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Invalid cookie name: " + name);
        }
        if (value.indexOf(';') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Invalid cookie value");
        }
    }
}
