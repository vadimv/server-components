package rsp.http;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketConnectionTests {
    @Test
    void generic_connection_delivers_binary_messages_to_listener() throws Exception {
        final CompletableFuture<byte[]> binary = new CompletableFuture<>();
        final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientSocket = new Socket("localhost", serverSocket.getLocalPort());
             Socket serverSideSocket = serverSocket.accept()) {
            Thread.startVirtualThread(() -> {
                try {
                    final WebSocketSession session = new WebSocketSession(serverSideSocket);
                    new WebSocketConnection(serverSideSocket, session, new WebSocketListener() {
                        @Override
                        public void onBinary(final byte[] payload) {
                            binary.complete(payload);
                        }

                        @Override
                        public void onClose(final int code, final String reason) {
                            closeCode.complete(code);
                        }
                    }).run();
                } catch (final Exception ex) {
                    binary.completeExceptionally(ex);
                    closeCode.completeExceptionally(ex);
                }
            });

            clientSocket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_BINARY, new byte[] {1, 2, 3}));
            clientSocket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_CLOSE,
                                                                   WebSocketFrame.closePayload(WebSocketFrame.CLOSE_NORMAL, "")));
            clientSocket.getOutputStream().flush();

            assertArrayEquals(new byte[] {1, 2, 3}, binary.get(2, TimeUnit.SECONDS));
            assertEquals(WebSocketFrame.CLOSE_NORMAL, closeCode.get(2, TimeUnit.SECONDS));
        }
    }

    private static byte[] maskedClientFrame(final int opcode, final byte[] payload) {
        final byte[] mask = new byte[] {0x01, 0x02, 0x03, 0x04};
        final byte[] frame = new byte[2 + mask.length + payload.length];
        frame[0] = (byte) (0x80 | opcode);
        frame[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, frame, 2, mask.length);
        for (int i = 0; i < payload.length; i++) {
            frame[2 + mask.length + i] = (byte) (payload[i] ^ mask[i % mask.length]);
        }
        return frame;
    }
}
