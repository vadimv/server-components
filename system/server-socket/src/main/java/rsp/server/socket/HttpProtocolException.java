package rsp.server.socket;

final class HttpProtocolException extends Exception {
    private final int status;

    HttpProtocolException(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
