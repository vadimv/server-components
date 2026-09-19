package rsp.http.middleware;

import rsp.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Bounded, transport-neutral data emitted once when an HTTP application completes. */
public record HttpAccessEvent(Instant startedAt,
                              HttpMethod method,
                              String rawPath,
                              Optional<String> requestId,
                              int statusCode,
                              Duration duration,
                              Optional<String> failureType) {
    public HttpAccessEvent {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rawPath, "rawPath");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (statusCode < 100 || statusCode > 999) {
            throw new IllegalArgumentException("statusCode must be between 100 and 999");
        }
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        failureType = Objects.requireNonNull(failureType, "failureType");
    }
}
