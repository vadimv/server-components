package rsp.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RspTransportProtocolTests {
    @Test
    void decodes_resume_and_acknowledgement_controls() throws Exception {
        final var resume = RspTransportProtocol.decodeClientControl("[7,1,42]").orElseThrow();
        final var acknowledge = RspTransportProtocol.decodeClientControl("[8,42]").orElseThrow();

        assertEquals(new RspTransportProtocol.Resume(1, 42), resume);
        assertEquals(new RspTransportProtocol.Acknowledge(42), acknowledge);
    }

    @Test
    void leaves_application_messages_for_the_existing_decoder() throws Exception {
        assertTrue(RspTransportProtocol.decodeClientControl("[6]").isEmpty());
    }

    @Test
    void rejects_invalid_control_shapes() {
        final WebSocketProtocolException exception = assertThrows(WebSocketProtocolException.class,
                () -> RspTransportProtocol.decodeClientControl("[7,1,-1]"));

        assertEquals(WebSocketFrame.CLOSE_PROTOCOL_ERROR, exception.closeCode());
        assertInstanceOf(WebSocketProtocolException.class, exception);
    }

    @Test
    void wraps_existing_application_json_without_reencoding_it() {
        assertEquals("[17,3,[4,[0,\"1\"]]]", RspTransportProtocol.frame(3, "[4,[0,\"1\"]]"));
        assertEquals("[18,3]", RspTransportProtocol.resumeAccepted(3));
        assertEquals("[19,\"gone\"]", RspTransportProtocol.resumeRejected("gone"));
    }
}
