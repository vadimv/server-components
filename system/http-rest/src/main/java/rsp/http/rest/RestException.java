package rsp.http.rest;

import rsp.http.HttpStatus;

import java.util.Objects;

/** An expected REST failure with a public, stable error code and message. */
public final class RestException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public RestException(HttpStatus status, String error, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.status = requireFailureStatus(status);
        this.error = requireError(error);
    }

    public RestException(HttpStatus status, String error, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.status = requireFailureStatus(status);
        this.error = requireError(error);
    }

    public static RestException badRequest(String error, String message) {
        return new RestException(HttpStatus.BAD_REQUEST, error, message);
    }

    public static RestException notFound(String error, String message) {
        return new RestException(HttpStatus.NOT_FOUND, error, message);
    }

    public static RestException conflict(String error, String message) {
        return new RestException(HttpStatus.CONFLICT, error, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    private static HttpStatus requireFailureStatus(HttpStatus status) {
        Objects.requireNonNull(status, "status");
        if (status.code() < 400 || status.code() > 599) {
            throw new IllegalArgumentException("REST failure status must be between 400 and 599");
        }
        return status;
    }

    private static String requireError(String error) {
        Objects.requireNonNull(error, "error");
        if (error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        return error;
    }
}
