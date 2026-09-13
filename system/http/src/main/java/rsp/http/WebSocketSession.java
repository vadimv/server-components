package rsp.http;

import rsp.metrics.MetricObjectTypes;
import rsp.metrics.Metrics;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class WebSocketSession {
    private final Socket socket;
    private final Metrics metrics;
    private final Object writeLock = new Object();
    private final AtomicBoolean closeSent = new AtomicBoolean();

    WebSocketSession(final Socket socket) {
        this(socket, Metrics.noop());
    }

    WebSocketSession(final Socket socket, final Metrics metrics) {
        this.socket = Objects.requireNonNull(socket);
        this.metrics = Objects.requireNonNull(metrics);
    }

    boolean isOpen() {
        return !closeSent.get() && !socket.isClosed();
    }

    void sendText(final String text) throws IOException {
        final byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (sendFrame(WebSocketFrame.OPCODE_TEXT, payload)) {
            recordMessageSent(payload.length);
        }
    }

    void sendBinary(final byte[] payload) throws IOException {
        final byte[] payloadCopy = Arrays.copyOf(payload, payload.length);
        if (sendFrame(WebSocketFrame.OPCODE_BINARY, payloadCopy)) {
            recordMessageSent(payloadCopy.length);
        }
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

    private boolean sendFrame(final int opcode, final byte[] payload) throws IOException {
        if (closeSent.get() && opcode != WebSocketFrame.OPCODE_CLOSE) {
            return false;
        }
        synchronized (writeLock) {
            WebSocketFrame.writeServerFrame(socket.getOutputStream(), opcode, payload);
        }
        return true;
    }

    private void recordMessageSent(final int payloadBytes) {
        metrics.incrementCounter(MetricObjectTypes.WEB_SOCKET_MESSAGES_SENT);
        metrics.incrementCounter(MetricObjectTypes.WEB_SOCKET_BYTES_SENT, payloadBytes);
    }
}
