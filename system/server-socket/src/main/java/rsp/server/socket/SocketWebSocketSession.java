package rsp.server.socket;

import rsp.websocket.WebSocketSession;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class SocketWebSocketSession implements WebSocketSession {
    private final Socket socket;
    private final SocketServerObserver.WebSocketObserver observer;
    private final Object writeLock = new Object();
    private final AtomicBoolean closeSent = new AtomicBoolean();

    SocketWebSocketSession(Socket socket, SocketServerObserver.WebSocketObserver observer) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    @Override
    public boolean isOpen() {
        return !closeSent.get() && !socket.isClosed();
    }

    @Override
    public void sendText(String text) throws IOException {
        byte[] payload = Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
        if (sendFrame(WebSocketFrame.OPCODE_TEXT, payload)) {
            observer.messageSent(payload.length);
        }
    }

    @Override
    public void sendBinary(byte[] payload) throws IOException {
        byte[] copy = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
        if (sendFrame(WebSocketFrame.OPCODE_BINARY, copy)) {
            observer.messageSent(copy.length);
        }
    }

    @Override
    public void close(int code, String reason) throws IOException {
        sendClosePayload(WebSocketFrame.closePayload(code, Objects.requireNonNull(reason, "reason")));
    }

    @Override
    public void abort() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best-effort transport cleanup.
        }
    }

    void sendPong(byte[] payload) throws IOException {
        sendFrame(WebSocketFrame.OPCODE_PONG, Arrays.copyOf(payload, payload.length));
    }

    void sendClosePayload(byte[] payload) throws IOException {
        if (closeSent.compareAndSet(false, true)) {
            sendFrame(WebSocketFrame.OPCODE_CLOSE, Arrays.copyOf(payload, payload.length));
        }
    }

    private boolean sendFrame(int opcode, byte[] payload) throws IOException {
        if (closeSent.get() && opcode != WebSocketFrame.OPCODE_CLOSE) {
            return false;
        }
        synchronized (writeLock) {
            WebSocketFrame.writeServerFrame(socket.getOutputStream(), opcode, payload);
        }
        return true;
    }
}
