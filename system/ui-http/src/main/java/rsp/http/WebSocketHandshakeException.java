package rsp.http;

final class WebSocketHandshakeException extends Exception {
    private final int status;

    WebSocketHandshakeException(final int status, final String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
