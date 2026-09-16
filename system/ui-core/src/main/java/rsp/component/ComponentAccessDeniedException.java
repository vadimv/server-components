package rsp.component;

/** Signals that a component cannot be rendered for the current application identity. */
public class ComponentAccessDeniedException extends RuntimeException {
    public ComponentAccessDeniedException(String message) {
        super(message);
    }

    public ComponentAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
