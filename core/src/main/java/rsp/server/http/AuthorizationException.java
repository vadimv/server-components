package rsp.server.http;

/**
 * Thrown when the user lacks required permissions.
 * Mapped to HTTP 403 Forbidden.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
