package rsp.http;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class WebSocketSession {
    private final Socket socket;
    private final Object writeLock = new Object();
    private final AtomicBoolean closeSent = new AtomicBoolean();

    WebSocketSession(final Socket socket) {
        this.socket = Objects.requireNonNull(socket);
    }

    boolean isOpen() {
        return !closeSent.get() && !socket.isClosed();
    }

    void sendText(final String text) throws IOException {
        sendFrame(WebSocketFrame.OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    void sendBinary(final byte[] payload) throws IOException {
        sendFrame(WebSocketFrame.OPCODE_BINARY, Arrays.copyOf(payload, payload.length));
    }

    void sendPong(final byte[] payload) throws IOException {
        sendFrame(WebSocketFrame.OPCODE_PONG, Arrays.copyOf(payload, payload.length));
    }

    void close(final int code, final String reason) throws IOException {
        sendClosePayload(WebSocketFrame.closePayload(code, reason));
    }

    void sendClosePayload(final byte[] payload) throws IOException {
        if (closeSent.compareAndSet(false, true)) {
            sendFrame(WebSocketFrame.OPCODE_CLOSE, Arrays.copyOf(payload, payload.length));
        }
    }

    void closeSocket() {
        try {
            socket.close();
        } catch (final IOException ignored) {
            // Best effort cleanup after a failed WebSocket write.
        }
    }

    private void sendFrame(final int opcode, final byte[] payload) throws IOException {
        if (closeSent.get() && opcode != WebSocketFrame.OPCODE_CLOSE) {
            return;
        }
        synchronized (writeLock) {
            WebSocketFrame.writeServerFrame(socket.getOutputStream(), opcode, payload);
        }
    }
}
