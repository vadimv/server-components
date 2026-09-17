package rsp.websocket;

/** A post-upgrade protocol failure carrying the RFC 6455 close code to send. */
public final class WebSocketProtocolException extends Exception {
    private final int closeCode;

    public WebSocketProtocolException(int closeCode, String message) {
        super(message);
        this.closeCode = closeCode;
    }

    public int closeCode() {
        return closeCode;
    }
}
