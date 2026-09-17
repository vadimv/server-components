package rsp.websocket;

import java.io.IOException;

/** Thread-safe outbound operations for one accepted WebSocket connection. */
public interface WebSocketSession {
    boolean isOpen();

    void sendText(String text) throws IOException;

    void sendBinary(byte[] payload) throws IOException;

    void close(int code, String reason) throws IOException;

    /** Immediately closes the underlying transport when graceful close is no longer possible. */
    void abort();
}
