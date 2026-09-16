package rsp.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketFrameTests {
    @Test
    void reads_masked_client_text_frame() throws Exception {
        final byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        final byte[] frameBytes = maskedFrame(0x80 | WebSocketFrame.OPCODE_TEXT, payload);

        final WebSocketFrame frame = WebSocketFrame.readClientFrame(new ByteArrayInputStream(frameBytes), 1024);

        assertEquals(WebSocketFrame.OPCODE_TEXT, frame.opcode());
        assertTrue(frame.fin());
        assertArrayEquals(payload, frame.payload());
    }

    @Test
    void rejects_unmasked_client_frame() {
        final byte[] frameBytes = new byte[] {(byte) 0x81, 0x00};

        final WebSocketProtocolException ex = assertThrows(WebSocketProtocolException.class,
                                                           () -> WebSocketFrame.readClientFrame(new ByteArrayInputStream(frameBytes), 1024));

        assertEquals(WebSocketFrame.CLOSE_PROTOCOL_ERROR, ex.closeCode());
    }

    @Test
    void rejects_fragmented_control_frame() {
        final byte[] frameBytes = maskedFrame(WebSocketFrame.OPCODE_PING, "x".getBytes(StandardCharsets.UTF_8));

        final WebSocketProtocolException ex = assertThrows(WebSocketProtocolException.class,
                                                           () -> WebSocketFrame.readClientFrame(new ByteArrayInputStream(frameBytes), 1024));

        assertEquals(WebSocketFrame.CLOSE_PROTOCOL_ERROR, ex.closeCode());
    }

    @Test
    void rejects_non_minimal_extended_length() {
        final byte[] frameBytes = new byte[] {
                (byte) 0x81,
                (byte) (0x80 | 126),
                0x00,
                0x05,
                0x01,
                0x02,
                0x03,
                0x04,
                'i',
                'g',
                'o',
                'h',
                'n'
        };

        final WebSocketProtocolException ex = assertThrows(WebSocketProtocolException.class,
                                                           () -> WebSocketFrame.readClientFrame(new ByteArrayInputStream(frameBytes), 1024));

        assertEquals(WebSocketFrame.CLOSE_PROTOCOL_ERROR, ex.closeCode());
    }

    @Test
    void writes_unmasked_server_frame() throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        WebSocketFrame.writeServerFrame(output,
                                        WebSocketFrame.OPCODE_TEXT,
                                        "ok".getBytes(StandardCharsets.UTF_8));

        final byte[] frame = output.toByteArray();
        assertEquals(0x80 | WebSocketFrame.OPCODE_TEXT, frame[0] & 0xFF);
        assertFalse((frame[1] & 0x80) != 0);
        assertEquals(2, frame[1] & 0x7F);
        assertEquals('o', frame[2]);
        assertEquals('k', frame[3]);
    }

    private static byte[] maskedFrame(final int firstByte, final byte[] payload) {
        final byte[] mask = new byte[] {0x01, 0x02, 0x03, 0x04};
        final byte[] frame = new byte[2 + mask.length + payload.length];
        frame[0] = (byte) firstByte;
        frame[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, frame, 2, mask.length);
        for (int i = 0; i < payload.length; i++) {
            frame[2 + mask.length + i] = (byte) (payload[i] ^ mask[i % mask.length]);
        }
        return frame;
    }
}
