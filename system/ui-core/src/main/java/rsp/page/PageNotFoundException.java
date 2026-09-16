package rsp.page;

/** Signals that a UI route or page resource does not exist. */
public class PageNotFoundException extends RuntimeException {
    public PageNotFoundException(String message) {
        super(message);
    }

    public PageNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
