package rsp.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static rsp.websocket.WebSocketCloseCodes.GOING_AWAY;
import static rsp.websocket.WebSocketCloseCodes.INVALID_PAYLOAD;
import static rsp.websocket.WebSocketCloseCodes.NORMAL;
import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;
import static rsp.websocket.WebSocketCloseCodes.UNSUPPORTED_DATA;

/** Wire constants/helpers used by UI transport-integration tests. */
final class WebSocketTestFrame {
    static final int OPCODE_CONTINUATION = 0x0;
    static final int OPCODE_TEXT = 0x1;
    static final int OPCODE_BINARY = 0x2;
    static final int OPCODE_CLOSE = 0x8;
    static final int OPCODE_PING = 0x9;
    static final int OPCODE_PONG = 0xA;

    static final int CLOSE_NORMAL = NORMAL;
    static final int CLOSE_GOING_AWAY = GOING_AWAY;
    static final int CLOSE_PROTOCOL_ERROR = PROTOCOL_ERROR;
    static final int CLOSE_UNSUPPORTED_DATA = UNSUPPORTED_DATA;
    static final int CLOSE_INVALID_PAYLOAD = INVALID_PAYLOAD;

    private WebSocketTestFrame() {
    }

    static byte[] closePayload(int code, String reason) {
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream payload = new ByteArrayOutputStream(2 + reasonBytes.length);
        payload.write((code >>> 8) & 0xFF);
        payload.write(code & 0xFF);
        payload.writeBytes(reasonBytes);
        return payload.toByteArray();
    }
}
