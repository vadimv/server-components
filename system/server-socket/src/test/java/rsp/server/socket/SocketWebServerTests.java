package rsp.server.socket;

import org.junit.jupiter.api.Test;
import rsp.http.HttpApplication;
import rsp.http.HttpResponse;
import rsp.websocket.WebSocketEndpoint;
import rsp.websocket.WebSocketListener;
import rsp.websocket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketWebServerTests {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void serves_a_transport_neutral_http_application() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        SocketWebServer server = started(new SocketWebServer(0,
                request -> CompletableFuture.completedFuture(HttpResponse.ok()
                        .header("X-Method", request.method().name())
                        .text(request.path().toString())
                        .build()), List.of(), 4, 2_000, observer));
        try {
            java.net.http.HttpResponse<String> response = client.send(
                    java.net.http.HttpRequest.newBuilder(uri(server, "/api/items/7")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("GET", response.headers().firstValue("X-Method").orElseThrow());
            assertEquals("/api/items/7", response.body());
            assertEquals(1, observer.requests.get());
            assertEquals(0, observer.failures.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void brackets_request_handling_with_application_lifecycle() throws Exception {
        List<String> calls = new ArrayList<>();
        HttpApplication application = new HttpApplication() {
            @Override
            public CompletionStage<HttpResponse> handle(rsp.http.HttpRequest request) {
                calls.add("handle");
                return CompletableFuture.completedFuture(HttpResponse.ok().build());
            }

            @Override
            public void start() {
                calls.add("start");
            }

            @Override
            public void stop() {
                calls.add("stop");
            }
        };
        SocketWebServer server = started(new SocketWebServer(0, application));
        try {
            client.send(java.net.http.HttpRequest.newBuilder(uri(server, "/one")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding());
            client.send(java.net.http.HttpRequest.newBuilder(uri(server, "/two")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding());

            assertEquals(List.of("start", "handle", "handle"), calls);
        } finally {
            server.stop();
        }
        assertEquals(List.of("start", "handle", "handle", "stop"), calls);
    }

    @Test
    void maps_failed_application_stages_to_sanitized_500_responses() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        SocketWebServer server = started(new SocketWebServer(0,
                _ -> CompletableFuture.failedFuture(new IllegalStateException("secret")),
                List.of(), 4, 2_000, observer));
        try {
            java.net.http.HttpResponse<String> response = client.send(
                    java.net.http.HttpRequest.newBuilder(uri(server, "/failure")).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            assertEquals(500, response.statusCode());
            assertTrue(!response.body().contains("secret"));
            assertEquals(1, observer.failures.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void hosts_a_generic_websocket_endpoint_with_subprotocol_and_traffic_observation() throws Exception {
        RecordingObserver observer = new RecordingObserver();
        EchoEndpoint endpoint = new EchoEndpoint();
        SocketWebServer server = started(new SocketWebServer(0,
                _ -> CompletableFuture.completedFuture(HttpResponse.status(rsp.http.HttpStatus.NOT_FOUND).build()),
                List.of(endpoint), 4, 2_000, observer));
        CompletableFuture<String> received = new CompletableFuture<>();
        try {
            WebSocket socket = client.newWebSocketBuilder().subprotocols("rsp-test")
                    .buildAsync(URI.create("ws://localhost:" + server.port() + "/echo"),
                            new WebSocket.Listener() {
                                @Override
                                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                    received.complete(data.toString());
                                    return CompletableFuture.completedFuture(null);
                                }
                            }).join();

            assertEquals("rsp-test", socket.getSubprotocol());
            socket.sendText("hello", true).join();
            assertEquals("echo:hello", received.get(2, TimeUnit.SECONDS));
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();

            await(() -> observer.activeWebSockets.get() == 0);
            assertEquals(1, observer.webSocketsOpened.get());
            assertEquals(5, observer.receivedBytes.get());
            assertEquals(10, observer.sentBytes.get());
        } finally {
            server.stop();
        }
    }

    private static SocketWebServer started(SocketWebServer server) {
        server.start();
        return server;
    }

    private static URI uri(SocketWebServer server, String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.getAsBoolean(); i++) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean());
    }

    private static final class EchoEndpoint implements WebSocketEndpoint {
        @Override
        public boolean matches(rsp.http.HttpRequest request) {
            return request.path().toString().equals("/echo");
        }

        @Override
        public List<String> supportedSubprotocols() {
            return List.of("rsp-test");
        }

        @Override
        public WebSocketListener open(rsp.http.HttpRequest request, WebSocketSession session) {
            return new WebSocketListener() {
                @Override
                public void onText(String message) throws java.io.IOException {
                    session.sendText("echo:" + message);
                }
            };
        }
    }

    private static final class RecordingObserver implements SocketServerObserver {
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicInteger activeWebSockets = new AtomicInteger();
        private final AtomicInteger webSocketsOpened = new AtomicInteger();
        private final AtomicInteger receivedBytes = new AtomicInteger();
        private final AtomicInteger sentBytes = new AtomicInteger();

        @Override
        public void requestReceived() {
            requests.incrementAndGet();
        }

        @Override
        public void requestFailed() {
            failures.incrementAndGet();
        }

        @Override
        public void activeWebSocketsChanged(int activeConnections) {
            activeWebSockets.set(activeConnections);
        }

        @Override
        public WebSocketObserver openWebSocket() {
            webSocketsOpened.incrementAndGet();
            return new WebSocketObserver() {
                @Override
                public void messageReceived(int payloadBytes) {
                    receivedBytes.addAndGet(payloadBytes);
                }

                @Override
                public void messageSent(int payloadBytes) {
                    sentBytes.addAndGet(payloadBytes);
                }
            };
        }
    }
}
