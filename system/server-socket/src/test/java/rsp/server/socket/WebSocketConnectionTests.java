package rsp.server.socket;

import org.junit.jupiter.api.Test;
import rsp.websocket.WebSocketListener;
import rsp.websocket.WebSocketProtocolException;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static rsp.websocket.WebSocketCloseCodes.NORMAL;
import static rsp.websocket.WebSocketCloseCodes.PROTOCOL_ERROR;

class WebSocketConnectionTests {
    private static final String DIAGNOSTIC_CANARY = "diagnostic-canary-secret";

    @Test
    void generic_connection_delivers_binary_messages_to_listener() throws Exception {
        CompletableFuture<byte[]> binary = new CompletableFuture<>();
        CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept()) {
            Thread.startVirtualThread(() -> {
                try {
                    SocketWebSocketSession session = new SocketWebSocketSession(serverSideSocket,
                            SocketServerObserver.WebSocketObserver.NOOP);
                    new WebSocketConnection(serverSideSocket, session, new WebSocketListener() {
                        @Override
                        public void onBinary(byte[] payload) {
                            binary.complete(payload);
                        }

                        @Override
                        public void onClose(int code, String reason) {
                            closeCode.complete(code);
                        }
                    }, SocketServerObserver.WebSocketObserver.NOOP, 2_000).run();
                } catch (Exception failure) {
                    binary.completeExceptionally(failure);
                    closeCode.completeExceptionally(failure);
                }
            });

            clientSocket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_BINARY,
                    new byte[] {1, 2, 3}));
            clientSocket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_CLOSE,
                    WebSocketFrame.closePayload(NORMAL, "")));
            clientSocket.getOutputStream().flush();

            assertArrayEquals(new byte[] {1, 2, 3}, binary.get(2, TimeUnit.SECONDS));
            assertEquals(NORMAL, closeCode.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void protocol_failure_does_not_expose_exception_message_as_close_reason() throws Exception {
        CompletableFuture<String> listenerCloseReason = new CompletableFuture<>();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept()) {
            Thread.startVirtualThread(() -> {
                try {
                    SocketWebSocketSession session = new SocketWebSocketSession(serverSideSocket,
                            SocketServerObserver.WebSocketObserver.NOOP);
                    new WebSocketConnection(serverSideSocket, session, new WebSocketListener() {
                        @Override
                        public void onText(String message) throws WebSocketProtocolException {
                            throw new WebSocketProtocolException(PROTOCOL_ERROR, DIAGNOSTIC_CANARY);
                        }

                        @Override
                        public void onClose(int code, String reason) {
                            listenerCloseReason.complete(reason);
                        }
                    }, SocketServerObserver.WebSocketObserver.NOOP, 2_000).run();
                } catch (Exception failure) {
                    listenerCloseReason.completeExceptionally(failure);
                }
            });

            clientSocket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_TEXT,
                    DIAGNOSTIC_CANARY.getBytes(StandardCharsets.UTF_8)));
            clientSocket.getOutputStream().flush();

            int first = clientSocket.getInputStream().read();
            int length = clientSocket.getInputStream().read() & 0x7F;
            byte[] closePayload = clientSocket.getInputStream().readNBytes(length);
            assertEquals(WebSocketFrame.OPCODE_CLOSE, first & 0x0F);
            assertEquals(PROTOCOL_ERROR, ((closePayload[0] & 0xFF) << 8) | (closePayload[1] & 0xFF));
            assertEquals(2, closePayload.length, "close frame must not contain a reason");
            assertEquals("", listenerCloseReason.get(2, TimeUnit.SECONDS));
        }
    }

    private static byte[] maskedClientFrame(int opcode, byte[] payload) {
        byte[] mask = new byte[] {0x01, 0x02, 0x03, 0x04};
        byte[] frame = new byte[2 + mask.length + payload.length];
        frame[0] = (byte) (0x80 | opcode);
        frame[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, frame, 2, mask.length);
        for (int i = 0; i < payload.length; i++) {
            frame[2 + mask.length + i] = (byte) (payload[i] ^ mask[i % mask.length]);
        }
        return frame;
    }
}
