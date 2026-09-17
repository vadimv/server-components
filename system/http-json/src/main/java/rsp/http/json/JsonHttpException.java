package rsp.http.json;

import rsp.http.HttpStatus;

import java.util.Objects;

/** A request-side JSON failure carrying the HTTP status appropriate for the client. */
public final class JsonHttpException extends IllegalArgumentException {
    private final HttpStatus status;

    public JsonHttpException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
    }

    public JsonHttpException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = Objects.requireNonNull(status, "status");
    }

    public HttpStatus status() {
        return status;
    }
}
