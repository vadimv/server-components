package rsp.http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

record WebSocketFrame(boolean fin, int opcode, byte[] payload) {
    static final int OPCODE_CONTINUATION = 0x0;
    static final int OPCODE_TEXT = 0x1;
    static final int OPCODE_BINARY = 0x2;
    static final int OPCODE_CLOSE = 0x8;
    static final int OPCODE_PING = 0x9;
    static final int OPCODE_PONG = 0xA;

    static final int CLOSE_NORMAL = 1000;
    static final int CLOSE_GOING_AWAY = 1001;
    static final int CLOSE_PROTOCOL_ERROR = 1002;
    static final int CLOSE_UNSUPPORTED_DATA = 1003;
    static final int CLOSE_INVALID_PAYLOAD = 1007;
    static final int CLOSE_MESSAGE_TOO_BIG = 1009;

    WebSocketFrame {
        payload = Arrays.copyOf(payload, payload.length);
    }

    static WebSocketFrame readClientFrame(final InputStream input,
                                          final int maxPayloadBytes) throws IOException, WebSocketProtocolException {
        final int first = readByte(input);
        final int second = readByte(input);
        final boolean fin = (first & 0x80) != 0;
        final boolean hasReservedBits = (first & 0x70) != 0;
        final int opcode = first & 0x0F;
        final boolean masked = (second & 0x80) != 0;
        final int lengthCode = second & 0x7F;

        if (hasReservedBits || !isKnownOpcode(opcode)) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "Invalid WebSocket frame header");
        }
        if (!masked) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "Client WebSocket frames must be masked");
        }

        final long payloadLength = payloadLength(input, lengthCode);
        if (isControl(opcode) && (!fin || payloadLength > 125)) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "Invalid WebSocket control frame");
        }
        if (payloadLength > maxPayloadBytes) {
            throw new WebSocketProtocolException(CLOSE_MESSAGE_TOO_BIG, "WebSocket message too large");
        }

        final byte[] mask = readBytes(input, 4);
        final byte[] payload = readBytes(input, Math.toIntExact(payloadLength));
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        return new WebSocketFrame(fin, opcode, payload);
    }

    static void writeServerFrame(final OutputStream output,
                                 final int opcode,
                                 final byte[] payload) throws IOException {
        output.write(0x80 | opcode);
        if (payload.length <= 125) {
            output.write(payload.length);
        } else if (payload.length <= 0xFFFF) {
            output.write(126);
            output.write((payload.length >>> 8) & 0xFF);
            output.write(payload.length & 0xFF);
        } else {
            output.write(127);
            output.write(ByteBuffer.allocate(Long.BYTES).putLong(payload.length).array());
        }
        output.write(payload);
        output.flush();
    }

    static byte[] closePayload(final int code, final String reason) {
        final byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        final ByteArrayOutputStream payload = new ByteArrayOutputStream(2 + reasonBytes.length);
        payload.write((code >>> 8) & 0xFF);
        payload.write(code & 0xFF);
        payload.writeBytes(reasonBytes);
        return payload.toByteArray();
    }

    private static long payloadLength(final InputStream input,
                                      final int lengthCode) throws IOException, WebSocketProtocolException {
        if (lengthCode < 126) {
            return lengthCode;
        }
        if (lengthCode == 126) {
            final int length = (readByte(input) << 8) | readByte(input);
            if (length < 126) {
                throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "Non-minimal WebSocket payload length");
            }
            return length;
        }
        long length = 0;
        for (int i = 0; i < Long.BYTES; i++) {
            length = (length << 8) | readByte(input);
        }
        if (length < 0x10000L || length < 0) {
            throw new WebSocketProtocolException(CLOSE_PROTOCOL_ERROR, "Invalid WebSocket payload length");
        }
        return length;
    }

    private static boolean isKnownOpcode(final int opcode) {
        return opcode == OPCODE_CONTINUATION
               || opcode == OPCODE_TEXT
               || opcode == OPCODE_BINARY
               || opcode == OPCODE_CLOSE
               || opcode == OPCODE_PING
               || opcode == OPCODE_PONG;
    }

    static boolean isControl(final int opcode) {
        return opcode >= 0x8;
    }

    private static int readByte(final InputStream input) throws IOException {
        final int value = input.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of WebSocket frame");
        }
        return value;
    }

    private static byte[] readBytes(final InputStream input, final int length) throws IOException {
        final byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException("Unexpected end of WebSocket frame");
        }
        return bytes;
    }
}
