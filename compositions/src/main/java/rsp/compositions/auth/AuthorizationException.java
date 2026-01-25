package rsp.compositions.auth;

/**
 * AuthorizationException - Thrown when user lacks required permissions.
 * <p>
 * Should be mapped to HTTP 403 Forbidden by the web server.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
