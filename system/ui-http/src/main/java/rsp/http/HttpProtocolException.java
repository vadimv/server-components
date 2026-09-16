package rsp.http;

final class HttpProtocolException extends Exception {
    private final int status;

    HttpProtocolException(final int status, final String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }
}
