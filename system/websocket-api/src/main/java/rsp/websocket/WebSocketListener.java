package rsp.websocket;

import java.io.IOException;

/** Complete-message events for one accepted WebSocket connection. */
public interface WebSocketListener {
    default void onOpen() throws IOException, WebSocketProtocolException {
    }

    default void onText(String message) throws IOException, WebSocketProtocolException {
    }

    default void onBinary(byte[] payload) throws IOException, WebSocketProtocolException {
    }

    default void onPong(byte[] payload) throws IOException, WebSocketProtocolException {
    }

    default void onClose(int code, String reason) {
    }

    default void onError(Throwable throwable) {
    }
}
