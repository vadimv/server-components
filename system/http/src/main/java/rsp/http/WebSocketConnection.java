package rsp.http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.DEBUG;

final class WebSocketConnection {
    static final int MAX_INBOUND_MESSAGE_BYTES = 256 * 1024;
    private static final int READ_TIMEOUT_MS = WebServer.DEFAULT_HEARTBEAT_INTERVAL_MS * 3;

    private static final System.Logger logger = System.getLogger(WebSocketConnection.class.getName());

    private final Socket socket;
    private final WebSocketSession session;
    private final WebSocketListener listener;
    private final CompletableFuture<Void> closed = new CompletableFuture<>();

    private int fragmentedOpcode = -1;
    private ByteArrayOutputStream fragmentedPayload;
    private volatile int closeCode = 1006;
    private volatile String closeReason = "";

    WebSocketConnection(final Socket socket,
                        final WebSocketSession session,
                        final WebSocketListener listener) {
        this.socket = Objects.requireNonNull(socket);
        this.session = Objects.requireNonNull(session);
        this.listener = Objects.requireNonNull(listener);
    }

    void run() throws IOException {
        socket.setSoTimeout(READ_TIMEOUT_MS);
        try {
            listener.onOpen();
            readLoop();
        } catch (final EOFException | SocketException ex) {
            logger.log(DEBUG, () -> "WebSocket closed");
        } catch (final WebSocketProtocolException ex) {
            closeCode = ex.closeCode();
            closeReason = ex.getMessage();
            session.close(closeCode, closeReason);
        } catch (final IOException | RuntimeException ex) {
            listener.onError(ex);
            throw ex;
        } finally {
            try {
                listener.onClose(closeCode, closeReason);
            } finally {
                closed.complete(null);
            }
        }
    }

    void initiateClose(final int code,
                       final String reason) {
        if (!session.isOpen()) {
            return;
        }
        closeCode = code;
        closeReason = reason;
        try {
            session.close(code, reason);
        } catch (final IOException ex) {
            logger.log(DEBUG, "Failed to send WebSocket close frame", ex);
            forceClose();
        }
    }

    void forceClose() {
        session.closeSocket();
    }

    CompletableFuture<Void> closed() {
        return closed;
    }

    private void readLoop() throws IOException, WebSocketProtocolException {
        while (!socket.isClosed()) {
            final WebSocketFrame frame = WebSocketFrame.readClientFrame(socket.getInputStream(), MAX_INBOUND_MESSAGE_BYTES);
            if (!handle(frame)) {
                return;
            }
        }
    }

    private boolean handle(final WebSocketFrame frame) throws IOException, WebSocketProtocolException {
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
            default -> throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Unsupported WebSocket opcode");
        };
    }

    private boolean handleData(final WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        if (fragmentedOpcode != -1) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Unexpected WebSocket data frame");
        }
        if (frame.fin()) {
            deliver(frame.opcode(), frame.payload());
            return session.isOpen();
        } else {
            fragmentedOpcode = frame.opcode();
            fragmentedPayload = new ByteArrayOutputStream();
            appendFragment(frame.payload());
        }
        return true;
    }

    private boolean handleContinuation(final WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        if (fragmentedOpcode == -1) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Unexpected WebSocket continuation frame");
        }
        appendFragment(frame.payload());
        if (frame.fin()) {
            final byte[] payload = fragmentedPayload.toByteArray();
            final int opcode = fragmentedOpcode;
            fragmentedOpcode = -1;
            fragmentedPayload = null;
            deliver(opcode, payload);
            return session.isOpen();
        }
        return true;
    }

    private boolean handleClose(final WebSocketFrame frame) throws IOException, WebSocketProtocolException {
        final CloseInfo closeInfo = closeInfo(frame.payload());
        closeCode = closeInfo.code();
        closeReason = closeInfo.reason();
        session.sendClosePayload(frame.payload());
        return false;
    }

    private void appendFragment(final byte[] payload) throws WebSocketProtocolException {
        if (fragmentedPayload.size() + payload.length > MAX_INBOUND_MESSAGE_BYTES) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_MESSAGE_TOO_BIG, "WebSocket message too large");
        }
        fragmentedPayload.writeBytes(payload);
    }

    private void deliver(final int opcode,
                         final byte[] payload) throws IOException, WebSocketProtocolException {
        if (opcode == WebSocketFrame.OPCODE_TEXT) {
            listener.onText(text(payload));
        } else if (opcode == WebSocketFrame.OPCODE_BINARY) {
            listener.onBinary(payload);
        } else {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Unexpected WebSocket opcode");
        }
    }

    private String text(final byte[] payload) throws WebSocketProtocolException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(payload))
                    .toString();
        } catch (final CharacterCodingException ex) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_INVALID_PAYLOAD, "Invalid UTF-8 WebSocket text message");
        }
    }

    private CloseInfo closeInfo(final byte[] payload) throws WebSocketProtocolException {
        if (payload.length == 0) {
            return new CloseInfo(WebSocketFrame.CLOSE_NORMAL, "");
        }
        if (payload.length == 1) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Invalid WebSocket close payload");
        }

        final int code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
        if (!isValidCloseCode(code)) {
            throw new WebSocketProtocolException(WebSocketFrame.CLOSE_PROTOCOL_ERROR, "Invalid WebSocket close code");
        }
        return new CloseInfo(code, text(java.util.Arrays.copyOfRange(payload, 2, payload.length)));
    }

    private boolean isValidCloseCode(final int code) {
        return switch (code) {
            case 1000, 1001, 1002, 1003, 1007, 1008, 1009, 1010, 1011 -> true;
            default -> code >= 3000 && code < 5000;
        };
    }

    private record CloseInfo(int code, String reason) {
    }
}
