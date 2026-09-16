package rsp.http;

final class WebSocketProtocolException extends Exception {
    private final int closeCode;

    WebSocketProtocolException(final int closeCode, final String message) {
        super(message);
        this.closeCode = closeCode;
    }

    int closeCode() {
        return closeCode;
    }
}
