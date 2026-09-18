package rsp.http.routing;

import rsp.url.Path;

import java.util.Objects;

/** Immutable metadata for a request selected by a literal path-prefix route. */
public record HttpPrefixContext(Path prefix, Path remainingPath) {
    public HttpPrefixContext {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(remainingPath, "remainingPath");
        if (!prefix.isAbsolute()) {
            throw new IllegalArgumentException("prefix must be absolute: " + prefix);
        }
        if (remainingPath.isAbsolute()) {
            throw new IllegalArgumentException("remainingPath must be relative: " + remainingPath);
        }
    }
}
