package rsp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;
import rsp.component.definitions.StatelessComponent.Unit;
import rsp.server.StaticResources;
import rsp.server.http.HttpRequest;

import java.net.URI;
import java.net.Socket;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpResponse.BodyHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rsp.dsl.Html.HeadType.PLAIN;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.h1;
import static rsp.dsl.Html.head;
import static rsp.dsl.Html.html;
import static rsp.dsl.Html.p;
import static rsp.dsl.Html.title;

class WebServerTests {
    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    private Path tempDir;

    @Test
    void serves_rendered_page_on_random_port() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("Hello from http")));
        try {
            assertTrue(server.port() > 0);

            final HttpResponse<String> response = client.send(get(server, "/hello?name=Codex"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Hello from http"));
            assertTrue(response.headers().firstValue("set-cookie").orElse("").contains("deviceId="));
        } finally {
            server.stop();
        }
    }

    @Test
    void merges_urlencoded_post_body_into_query_parameters() throws Exception {
        final WebServer server = started(new WebServer(0, request ->
                page(request.queryParameters.parameterValue("firstname") + " "
                     + request.queryParameters.parameterValue("lastname"))));
        try {
            final java.net.http.HttpRequest post = java.net.http.HttpRequest.newBuilder(uri(server, "/form"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(BodyPublishers.ofString("firstname=Ada&lastname=Lovelace"))
                    .build();

            final HttpResponse<String> response = client.send(post, BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Ada Lovelace"));
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_static_resources() throws Exception {
        Files.writeString(tempDir.resolve("style.css"), "body { color: red; }");
        final WebServer server = started(new WebServer(0,
                                                       _ -> page("not static"),
                                                       new StaticResources(tempDir.toFile(), "/res/")));
        try {
            final HttpResponse<String> response = client.send(get(server, "/res/style.css"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("text/css", response.headers().firstValue("content-type").orElse(null));
            assertEquals("body { color: red; }", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_js_client_bundle_from_runtime_dependency() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("bundle")));
        try {
            final HttpResponse<String> response = client.send(get(server, "/static/js-client.min.js"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("application/javascript", response.headers().firstValue("content-type").orElse(null));
            assertTrue(response.body().length() > 100);
        } finally {
            server.stop();
        }
    }

    @Test
    void head_request_omits_response_body() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("head body")));
        try {
            final java.net.http.HttpRequest head = java.net.http.HttpRequest.newBuilder(uri(server, "/head"))
                    .method("HEAD", BodyPublishers.noBody())
                    .build();

            final HttpResponse<String> response = client.send(head, BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("", response.body());
        } finally {
            server.stop();
        }
    }

    @Test
    void unsupported_methods_return_405() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("delete")));
        try {
            final java.net.http.HttpRequest delete = java.net.http.HttpRequest.newBuilder(uri(server, "/delete"))
                    .DELETE()
                    .build();

            final HttpResponse<String> response = client.send(delete, BodyHandlers.ofString());

            assertEquals(405, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void rejects_non_positive_connection_limit() {
        assertThrows(IllegalArgumentException.class,
                     () -> new WebServer(0,
                                         _ -> page("limit"),
                                         Optional.empty(),
                                         Optional.empty(),
                                         0));
    }

    @Test
    void upgrades_to_websocket_and_binds_live_page_session() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("live")));
        try {
            client.send(get(server, "/live"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            final CompletableFuture<String> firstText = new CompletableFuture<>();

            final WebSocket webSocket = client.newWebSocketBuilder()
                    .buildAsync(webSocketUri(server, sessionId), new TestWebSocketListener(firstText, new CompletableFuture<>()))
                    .join();

            assertEquals("[0,0]", firstText.get(2, TimeUnit.SECONDS));
            assertTrue(server.pagesStorage.isEmpty());
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        } finally {
            server.stop();
        }
    }

    @Test
    void responds_to_websocket_ping_with_pong_payload() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("ping")));
        try {
            client.send(get(server, "/ping"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            final CompletableFuture<String> firstText = new CompletableFuture<>();
            final CompletableFuture<ByteBuffer> pong = new CompletableFuture<>();
            final WebSocket webSocket = client.newWebSocketBuilder()
                    .buildAsync(webSocketUri(server, sessionId), new TestWebSocketListener(firstText, pong))
                    .join();

            assertEquals("[0,0]", firstText.get(2, TimeUnit.SECONDS));
            webSocket.sendPing(ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8))).join();

            assertEquals("abc", StandardCharsets.UTF_8.decode(pong.get(2, TimeUnit.SECONDS)).toString());
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        } finally {
            server.stop();
        }
    }

    @Test
    void websocket_handshake_returns_rfc_accept_key() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("handshake")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device", "session", "dGhlIHNhbXBsZSBub25jZQ==");

            final String response = readHttpHeaders(socket);

            assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols"));
            assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));
        } finally {
            server.stop();
        }
    }

    @Test
    void invalid_websocket_key_returns_http_400() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("bad key")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device", "session", "not-base64");

            final String response = readHttpHeaders(socket);

            assertTrue(response.startsWith("HTTP/1.1 400 Bad Request"));
        } finally {
            server.stop();
        }
    }

    @Test
    void websocket_upgrade_to_unknown_endpoint_returns_404() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("unknown ws")));
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.getOutputStream().write(("GET /other-web-socket HTTP/1.1\r\n"
                                            + "Host: localhost:" + server.port() + "\r\n"
                                            + "Upgrade: websocket\r\n"
                                            + "Connection: Upgrade\r\n"
                                            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                                            + "Sec-WebSocket-Version: 13\r\n"
                                            + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 404 Not Found"));
        } finally {
            server.stop();
        }
    }

    @Test
    void rsp_websocket_endpoint_without_standard_upgrade_headers_returns_400() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("missing headers")));
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.getOutputStream().write(("GET /bridge/web-socket/device/session HTTP/1.1\r\n"
                                            + "Host: localhost:" + server.port() + "\r\n"
                                            + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();

            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 400 Bad Request"));
        } finally {
            server.stop();
        }
    }

    @Test
    void unmasked_client_websocket_frame_closes_with_protocol_error() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("unmasked")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-unmasked", "session-unmasked", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(new byte[] {(byte) 0x81, 0x00});
            socket.getOutputStream().flush();

            assertEquals(WebSocketFrame.CLOSE_PROTOCOL_ERROR, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    @Test
    void invalid_utf8_websocket_text_closes_with_invalid_payload() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("utf8")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-utf8", "session-utf8", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_TEXT, new byte[] {(byte) 0xC3, 0x28}));
            socket.getOutputStream().flush();

            assertEquals(WebSocketFrame.CLOSE_INVALID_PAYLOAD, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    @Test
    void fragmented_websocket_text_message_is_reassembled() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("fragmented")));
        try (Socket socket = new Socket("localhost", server.port())) {
            client.send(get(server, "/fragmented"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            writeHandshake(socket, server.port(), sessionId.deviceId(), sessionId.sessionId(), "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));
            readServerFrame(socket);

            socket.getOutputStream().write(maskedClientFrame(false, WebSocketFrame.OPCODE_TEXT, "[".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().write(maskedClientFrame(true, WebSocketFrame.OPCODE_CONTINUATION, "6]".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_PING, "ok".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().flush();

            final RawServerFrame pong = readFrameWithOpcode(socket, WebSocketFrame.OPCODE_PONG);
            assertEquals("ok", new String(pong.payload, StandardCharsets.UTF_8));
        } finally {
            server.stop();
        }
    }

    @Test
    void binary_websocket_message_closes_with_unsupported_data() throws Exception {
        final WebServer server = started(new WebServer(0, _ -> page("binary")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-binary", "session-binary", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(maskedClientFrame(WebSocketFrame.OPCODE_BINARY, new byte[] {1, 2, 3}));
            socket.getOutputStream().flush();

            assertEquals(WebSocketFrame.CLOSE_UNSUPPORTED_DATA, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    private static WebServer started(final WebServer server) {
        server.start();
        return server;
    }

    private static java.net.http.HttpRequest get(final WebServer server, final String path) {
        return java.net.http.HttpRequest.newBuilder(uri(server, path)).GET().build();
    }

    private static URI uri(final WebServer server, final String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static URI webSocketUri(final WebServer server, final rsp.page.QualifiedSessionId sessionId) {
        return URI.create("ws://localhost:" + server.port()
                          + "/bridge/web-socket/" + sessionId.deviceId() + "/" + sessionId.sessionId());
    }

    private static void writeHandshake(final Socket socket,
                                       final int port,
                                       final String deviceId,
                                       final String sessionId,
                                       final String key) throws Exception {
        socket.getOutputStream().write(("GET /bridge/web-socket/" + deviceId + "/" + sessionId + " HTTP/1.1\r\n"
                                        + "Host: localhost:" + port + "\r\n"
                                        + "Upgrade: websocket\r\n"
                                        + "Connection: Upgrade\r\n"
                                        + "Sec-WebSocket-Key: " + key + "\r\n"
                                        + "Sec-WebSocket-Version: 13\r\n"
                                        + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        socket.getOutputStream().flush();
    }

    private static String readHttpHeaders(final Socket socket) throws Exception {
        final StringBuilder headers = new StringBuilder();
        int b1 = -1;
        int b2 = -1;
        int b3 = -1;
        int b4 = -1;
        while (true) {
            final int next = socket.getInputStream().read();
            if (next < 0) {
                break;
            }
            headers.append((char) next);
            b1 = b2;
            b2 = b3;
            b3 = b4;
            b4 = next;
            if (b1 == '\r' && b2 == '\n' && b3 == '\r' && b4 == '\n') {
                return headers.toString();
            }
        }
        return headers.toString();
    }

    private static byte[] maskedClientFrame(final int opcode, final byte[] payload) {
        return maskedClientFrame(true, opcode, payload);
    }

    private static byte[] maskedClientFrame(final boolean fin, final int opcode, final byte[] payload) {
        final byte[] mask = new byte[] {0x05, 0x06, 0x07, 0x08};
        final byte[] frame = new byte[2 + mask.length + payload.length];
        frame[0] = (byte) ((fin ? 0x80 : 0x00) | opcode);
        frame[1] = (byte) (0x80 | payload.length);
        System.arraycopy(mask, 0, frame, 2, mask.length);
        for (int i = 0; i < payload.length; i++) {
            frame[2 + mask.length + i] = (byte) (payload[i] ^ mask[i % mask.length]);
        }
        return frame;
    }

    private static int readCloseCode(final Socket socket) throws Exception {
        for (int i = 0; i < 4; i++) {
            final RawServerFrame frame = readServerFrame(socket);
            if (frame.opcode == WebSocketFrame.OPCODE_CLOSE) {
                return ((frame.payload[0] & 0xFF) << 8) | (frame.payload[1] & 0xFF);
            }
        }
        throw new AssertionError("Close frame not received");
    }

    private static RawServerFrame readFrameWithOpcode(final Socket socket, final int opcode) throws Exception {
        for (int i = 0; i < 4; i++) {
            final RawServerFrame frame = readServerFrame(socket);
            if (frame.opcode == opcode) {
                return frame;
            }
        }
        throw new AssertionError("Expected WebSocket frame not received, opcode=" + opcode);
    }

    private static RawServerFrame readServerFrame(final Socket socket) throws Exception {
        final int first = socket.getInputStream().read();
        final int second = socket.getInputStream().read();
        final int opcode = first & 0x0F;
        final int lengthCode = second & 0x7F;
        final int length;
        if (lengthCode < 126) {
            length = lengthCode;
        } else if (lengthCode == 126) {
            length = (socket.getInputStream().read() << 8) | socket.getInputStream().read();
        } else {
            throw new AssertionError("Unexpected long server frame in test");
        }
        final byte[] payload = socket.getInputStream().readNBytes(length);
        return new RawServerFrame(opcode, payload);
    }

    private static Component<?, ?> page(final String text) {
        return new StatelessComponent((rsp.component.View<Unit>) _ -> html(head(PLAIN, title("HTTP test")),
                                                                           body(h1(text), p("served"))));
    }

    private record RawServerFrame(int opcode, byte[] payload) {
    }

    private static final class TestWebSocketListener implements WebSocket.Listener {
        private final CompletableFuture<String> firstText;
        private final CompletableFuture<ByteBuffer> pong;

        private TestWebSocketListener(final CompletableFuture<String> firstText,
                                      final CompletableFuture<ByteBuffer> pong) {
            this.firstText = firstText;
            this.pong = pong;
        }

        @Override
        public void onOpen(final WebSocket webSocket) {
            webSocket.request(10);
        }

        @Override
        public CompletionStage<?> onText(final WebSocket webSocket,
                                         final CharSequence data,
                                         final boolean last) {
            firstText.complete(data.toString());
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onPong(final WebSocket webSocket,
                                         final ByteBuffer message) {
            final ByteBuffer copy = ByteBuffer.allocate(message.remaining());
            copy.put(message);
            copy.flip();
            pong.complete(copy);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }
    }
}
