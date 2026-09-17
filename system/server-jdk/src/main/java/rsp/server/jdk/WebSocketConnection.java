package rsp.server.jdk;

import rsp.websocket.WebSocketListener;
import rsp.websocket.WebSocketProtocolException;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.DEBUG;
import static rsp.websocket.WebSocketCloseCodes.INVALID_PAYLOAD;
import static rsp.websocket.WebSocketCloseCodes.MESSAGE_TOO_BIG;
import static rsp.websocket.WebSocketCloseCodes.NORMAL;
import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;

final class WebSocketConnection {
    static final int MAX_INBOUND_MESSAGE_BYTES = 256 * 1024;
    private static final System.Logger logger = System.getLogger(WebSocketConnection.class.getName());

    private final Socket socket;
    private final JdkWebSocketSession session;
    private final WebSocketListener listener;
    private final JdkServerObserver.WebSocketObserver observer;
    private final int readTimeoutMs;
    private final CompletableFuture<Void> closed = new CompletableFuture<>();

    private int fragmentedOpcode = -1;
    private ByteArrayOutputStream fragmentedPayload;
    private volatile int closeCode = 1006;
    private volatile String closeReason = "";

    WebSocketConnection(Socket socket, JdkWebSocketSession session, WebSocketListener listener,
                        JdkServerObserver.WebSocketObserver observer, int readTimeoutMs) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.session = Objects.requireNonNull(session, "session");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.observer = Objects.requireNonNull(observer, "observer");
        this.readTimeoutMs = readTimeoutMs;
    }

    void run() throws IOException {
        socket.setSoTimeout(readTimeoutMs);
        try {
            listener.onOpen();
            readLoop();
        } catch (EOFException | SocketException failure) {
            logger.log(DEBUG, "WebSocket closed");
        } catch (WebSocketProtocolException failure) {
            closeCode = failure.closeCode();
            closeReason = "";
            session.close(closeCode, closeReason);
        } catch (IOException | RuntimeException failure) {
            listener.onError(failure);
            throw failure;
        } finally {
            try {
                listener.onClose(closeCode, closeReason);
            } finally {
                closed.complete(null);
            }
        }
    }

    void initiateClose(int code, String reason) {
        if (!session.isOpen()) {
            return;
        }
        closeCode = code;
        closeReason = reason;
        try {
            session.close(code, reason);
        } catch (IOException failure) {
            session.abort();
        }
    }

    void forceClose() {
        session.abort();
    }

    CompletableFuture<Void> closed() {
        return closed;
    }

    private void readLoop() throws IOException, WebSocketProtocolException {
        while (!socket.isClosed()) {
            WebSocketFrame frame = WebSocketFrame.readClientFrame(socket.getInputStream(), MAX_INBOUND_MESSAGE_BYTES);
            if (!handle(frame)) {
                return;
            }
        }
    }

    private boolean handle(WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        return switch (frame.opcode()) {
            case WebSocketFrame.OPCODE_CLOSE -> handleClose(frame);
            case WebSocketFrame.OPCODE_PING -> {
                session.sendPong(frame.payload());
                yield true;
            }
            case WebSocketFrame.OPCODE_PONG -> {
                listener.onPong(frame.payload());
                yield true;
            }
            case WebSocketFrame.OPCODE_TEXT, WebSocketFrame.OPCODE_BINARY -> handleData(frame);
            case WebSocketFrame.OPCODE_CONTINUATION -> handleContinuation(frame);
            default -> throw new WebSocketProtocolException(PROTOCOL_ERROR, "Unsupported WebSocket opcode");
        };
    }

    private boolean handleData(WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        if (fragmentedOpcode != -1) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Unexpected WebSocket data frame");
        }
        if (frame.fin()) {
            deliver(frame.opcode(), frame.payload());
            return session.isOpen();
        }
        fragmentedOpcode = frame.opcode();
        fragmentedPayload = new ByteArrayOutputStream();
        appendFragment(frame.payload());
        return true;
    }

    private boolean handleContinuation(WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        if (fragmentedOpcode == -1) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Unexpected WebSocket continuation frame");
        }
        appendFragment(frame.payload());
        if (frame.fin()) {
            byte[] payload = fragmentedPayload.toByteArray();
            int opcode = fragmentedOpcode;
            fragmentedOpcode = -1;
            fragmentedPayload = null;
            deliver(opcode, payload);
            return session.isOpen();
        }
        return true;
    }

    private boolean handleClose(WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        CloseInfo close = closeInfo(frame.payload());
        closeCode = close.code();
        closeReason = close.reason();
        session.sendClosePayload(frame.payload());
        return false;
    }

    private void appendFragment(byte[] payload) throws WebSocketProtocolException {
        if (fragmentedPayload.size() + payload.length > MAX_INBOUND_MESSAGE_BYTES) {
            throw new WebSocketProtocolException(MESSAGE_TOO_BIG, "WebSocket message too large");
        }
        fragmentedPayload.writeBytes(payload);
    }

    private void deliver(int opcode, byte[] payload) throws IOException, WebSocketProtocolException {
        observer.messageReceived(payload.length);
        if (opcode == WebSocketFrame.OPCODE_TEXT) {
            listener.onText(text(payload));
        } else if (opcode == WebSocketFrame.OPCODE_BINARY) {
            listener.onBinary(Arrays.copyOf(payload, payload.length));
        } else {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Unexpected WebSocket opcode");
        }
    }

    private String text(byte[] payload) throws WebSocketProtocolException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException failure) {
            throw new WebSocketProtocolException(INVALID_PAYLOAD, "Invalid UTF-8 WebSocket text message");
        }
    }

    private CloseInfo closeInfo(byte[] payload) throws WebSocketProtocolException {
        if (payload.length == 0) {
            return new CloseInfo(NORMAL, "");
        }
        if (payload.length == 1) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Invalid WebSocket close payload");
        }
        int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        if (!isValidCloseCode(code)) {
            throw new WebSocketProtocolException(PROTOCOL_ERROR, "Invalid WebSocket close code");
        }
        return new CloseInfo(code, text(Arrays.copyOfRange(payload, 2, payload.length)));
    }

    private boolean isValidCloseCode(int code) {
        return switch (code) {
            case 1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011 -> true;
            default -> code >= 3000 && code < 5000;
        };
    }

    private record CloseInfo(int code, String reason) {
    }
}
