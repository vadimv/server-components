package rsp.http;

import java.io.IOException;

interface WebSocketListener {
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
