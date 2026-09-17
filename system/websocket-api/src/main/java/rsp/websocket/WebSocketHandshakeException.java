package rsp.websocket;

import rsp.http.HttpStatus;

import java.util.Objects;

/** A policy or protocol rejection that must be returned before a WebSocket upgrade. */
public final class WebSocketHandshakeException extends Exception {
    private final HttpStatus status;

    public WebSocketHandshakeException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
    }

    public WebSocketHandshakeException(int status, String message) {
        this(HttpStatus.of(status), message);
    }

    public HttpStatus status() {
        return status;
    }
}
