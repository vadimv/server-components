package rsp.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rsp.application.ApplicationLifecycle;
import rsp.component.definitions.Component;
import rsp.component.definitions.StatelessComponent;
import rsp.component.definitions.StatelessComponent.Unit;
import rsp.metrics.MetricNames;
import rsp.metrics.MetricObjectTypes;
import rsp.metrics.MetricRegistry;
import rsp.http.StaticResources;
import rsp.component.ComponentAccessDeniedException;
import rsp.http.HttpRequest;
import rsp.page.PageNotFoundException;
import rsp.http.routing.HttpRouteHandler;

import java.net.URI;
import java.net.Socket;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpResponse.BodyHandlers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rsp.dsl.Html.body;
import static rsp.dsl.Html.h1;
import static rsp.dsl.Html.head;
import static rsp.dsl.Html.html;
import static rsp.dsl.Html.p;
import static rsp.dsl.Html.title;

class WebServerTests {
    private static final String DIAGNOSTIC_CANARY = "diagnostic-canary-secret";

    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    private Path tempDir;

    @Test
    void serves_additive_page_routes_with_path_parameters_on_random_port() throws Exception {
        final WebServer server = started(new WebServer(0)
                .page("/hello/{name}", (_, route) ->
                        page("Hello, " + route.requiredParameter("name")))
                .page("/about", (_, _) -> page("About")));
        try {
            assertTrue(server.port() > 0);

            final HttpResponse<String> response = client.send(get(server, "/hello/Codex?source=test"),
                                                              BodyHandlers.ofString());
            final HttpResponse<String> about = client.send(get(server, "/about"), BodyHandlers.ofString());
            final HttpResponse<String> missing = client.send(get(server, "/missing"), BodyHandlers.ofString());
            final HttpResponse<String> wrongMethod = client.send(java.net.http.HttpRequest.newBuilder(
                            uri(server, "/about"))
                    .POST(BodyPublishers.noBody())
                    .build(), BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Hello, Codex"));
            assertTrue(response.headers().firstValue("set-cookie").orElse("").contains("deviceId="));
            assertEquals(200, about.statusCode());
            assertTrue(about.body().contains("About"));
            assertEquals(404, missing.statusCode());
            assertEquals(405, wrongMethod.statusCode());
            assertEquals("GET, HEAD", wrongMethod.headers().firstValue("Allow").orElseThrow());
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_generic_http_routes_and_ui_pages_on_the_same_port() throws Exception {
        AtomicInteger pageRequests = new AtomicInteger();
        PageApplication pages = request -> {
            pageRequests.incrementAndGet();
            return PageResult.staticHtml(page("dashboard"));
        };
        Router routes = HttpRouter.builder()
                .get("/api/items/{id}", (_, route) -> rsp.http.HttpResponse.ok()
                        .header("X-Item", route.requiredParameter("id"))
                        .text("api")
                        .build())
                .build();
        WebServer server = started(new WebServer(0).pageApplication(pages).routes(routes));
        try {
            HttpResponse<String> api = client.send(get(server, "/api/items/42"), BodyHandlers.ofString());
            HttpResponse<String> page = client.send(get(server, "/dashboard"), BodyHandlers.ofString());
            java.net.http.HttpRequest wrongMethod = java.net.http.HttpRequest.newBuilder(
                            uri(server, "/api/items/42"))
                    .POST(BodyPublishers.noBody())
                    .build();
            HttpResponse<String> disallowed = client.send(wrongMethod, BodyHandlers.ofString());

            assertEquals(200, api.statusCode());
            assertEquals("api", api.body());
            assertEquals("42", api.headers().firstValue("X-Item").orElseThrow());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("dashboard"));
            assertEquals(405, disallowed.statusCode());
            assertEquals("GET, HEAD", disallowed.headers().firstValue("Allow").orElseThrow());
            assertEquals(1, pageRequests.get());
        } finally {
            server.stop();
        }
    }

    @Test
    void serves_exact_method_aware_page_routes_with_path_parameters() throws Exception {
        Router routes = HttpRouter.builder()
                .get("/forms/{name}", (_, route) ->
                        PageResult.staticHtml(page("form " + route.requiredParameter("name"))))
                .post("/forms/{name}", (_, route) ->
                        PageResult.staticHtml(page("submitted " + route.requiredParameter("name"))))
                .build();
        WebServer server = started(new WebServer(0).routes(routes));
        try {
            HttpResponse<String> get = client.send(get(server, "/forms/Ada"), BodyHandlers.ofString());
            java.net.http.HttpRequest post = java.net.http.HttpRequest.newBuilder(uri(server, "/forms/Ada"))
                    .POST(BodyPublishers.noBody())
                    .build();
            HttpResponse<String> submitted = client.send(post, BodyHandlers.ofString());
            java.net.http.HttpRequest put = java.net.http.HttpRequest.newBuilder(uri(server, "/forms/Ada"))
                    .PUT(BodyPublishers.noBody())
                    .build();
            HttpResponse<String> disallowed = client.send(put, BodyHandlers.ofString());
            HttpResponse<String> missing = client.send(get(server, "/other"), BodyHandlers.ofString());

            assertEquals(200, get.statusCode());
            assertTrue(get.body().contains("form Ada"));
            assertEquals(200, submitted.statusCode());
            assertTrue(submitted.body().contains("submitted Ada"));
            assertEquals(405, disallowed.statusCode());
            assertEquals("GET, HEAD, POST", disallowed.headers().firstValue("Allow").orElseThrow());
            assertEquals(404, missing.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void combines_page_and_generic_handlers_for_different_methods_on_one_path() throws Exception {
        Router routes = HttpRouter.builder()
                .get("/mixed", (_, _) -> rsp.http.HttpResponse.ok().text("generic").build())
                .post("/mixed", (_, _) -> PageResult.staticHtml(page("page")))
                .build();
        WebServer server = started(new WebServer(0).routes(routes));
        try {
            HttpResponse<String> get = client.send(get(server, "/mixed"), BodyHandlers.ofString());
            java.net.http.HttpRequest post = java.net.http.HttpRequest.newBuilder(uri(server, "/mixed"))
                    .POST(BodyPublishers.noBody())
                    .build();
            HttpResponse<String> submitted = client.send(post, BodyHandlers.ofString());

            assertEquals(200, get.statusCode());
            assertEquals("generic", get.body());
            assertEquals(200, submitted.statusCode());
            assertTrue(submitted.body().contains("page"));
        } finally {
            server.stop();
        }
    }

    @Test
    void router_rejects_ambiguous_templates_across_handler_kinds() {
        HttpRouter.Builder routes = HttpRouter.builder()
                .get("/users/{id}", (_, _) -> PageResult.staticHtml(page("id")))
                .get("/users/{name}", (_, _) -> rsp.http.HttpResponse.ok().build());

        assertThrows(IllegalArgumentException.class, routes::build);
    }

    @Test
    void middleware_wraps_the_complete_http_route_graph() {
        Router routes = HttpRouter.builder()
                .getAsync("/api", HttpRouteHandler.sync((_, _) -> rsp.http.HttpResponse.ok().build()))
                .build();
        HttpMiddleware marker = (request, next) -> next.handle(request)
                .thenApply(response -> response.withHeader("X-Middleware", "applied"));
        WebServer server = new WebServer(0)
                .pageApplication(_ -> PageResult.staticHtml(page("fallback")))
                .routes(routes)
                .middleware(marker);
        rsp.http.HttpRequest request = new rsp.http.HttpRequest(HttpMethod.GET, "/api", "/api",
                URI.create("http://localhost/api"), "http://localhost/api",
                rsp.url.Path.parse("/api"), rsp.url.Query.EMPTY, HttpHeaders.EMPTY, RequestBody.EMPTY);

        rsp.http.HttpResponse response = server.httpApplication().handle(request).toCompletableFuture().join();

        assertEquals("applied", response.header("X-Middleware"));
    }

    @Test
    void freezes_fluent_configuration_when_the_runtime_is_created() {
        WebServer server = new WebServer(0)
                .pageApplication(_ -> PageResult.staticHtml(page("configured")));

        server.httpApplication();

        assertThrows(IllegalStateException.class,
                () -> server.pageApplication(_ -> PageResult.staticHtml(page("too late"))));
        assertThrows(IllegalStateException.class,
                () -> server.routes(HttpRouter.builder().build()));
    }

    @Test
    void starts_page_lifecycle_once_and_stops_it_after_all_pages_close() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicInteger stops = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();
        ApplicationLifecycle lifecycle = new ApplicationLifecycle() {
            @Override
            public void start() {
                starts.incrementAndGet();
            }

            @Override
            public void stop() {
                stops.incrementAndGet();
            }
        };
        PageApplication pages = PageApplication.withLifecycle(lifecycle, _ -> {
            requests.incrementAndGet();
            return PageResult.staticHtml(page("lifecycle"));
        });
        WebServer server = started(new WebServer(0).pageApplication(pages));
        try {
            assertThrows(IllegalStateException.class, server::start);
            client.send(get(server, "/one"), BodyHandlers.discarding());
            client.send(get(server, "/two"), BodyHandlers.discarding());

            assertEquals(1, starts.get());
            assertEquals(0, stops.get());
            assertEquals(2, requests.get());
        } finally {
            server.stop();
        }
        assertEquals(1, stops.get());
    }

    @Test
    void records_http_and_component_metrics_in_the_injected_process_registry() throws Exception {
        final MetricRegistry metrics = new MetricRegistry(MetricNames.frameworkCatalog());
        final WebServer server = started(new WebServer(0)
                .page("/instrumented", (_, _) -> page("instrumented"))
                .metrics(metrics));
        try {
            final HttpResponse<String> response = client.send(get(server, "/instrumented"),
                                                              BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(1, metrics.value(MetricNames.HTTP_REQUESTS));
            assertEquals(0, metrics.value(MetricNames.HTTP_FAILURES));
            assertTrue(metrics.value(MetricNames.SEGMENT_CREATED) > 0);
        } finally {
            server.stop();
        }
    }

    @Test
    void records_failed_http_responses_without_exposing_failure_details() throws Exception {
        final MetricRegistry metrics = new MetricRegistry(MetricNames.frameworkCatalog());
        final WebServer server = started(new WebServer(0)
                .page("/failure", (_, _) -> failingPage(new RuntimeException(DIAGNOSTIC_CANARY)))
                .metrics(metrics));
        try {
            final HttpResponse<String> response = client.send(get(server, "/failure"),
                                                              BodyHandlers.ofString());

            assertEquals(500, response.statusCode());
            assertFalse(response.body().contains(DIAGNOSTIC_CANARY));
            assertEquals(1, metrics.value(MetricNames.HTTP_REQUESTS));
            assertEquals(1, metrics.value(MetricNames.HTTP_FAILURES));
        } finally {
            server.stop();
        }
    }

    @Test
    void merges_urlencoded_post_body_into_query_parameters() throws Exception {
        final WebServer server = started(new WebServer(0).pageApplication(request ->
                PageResult.live(page(request.query().parameterValue("firstname") + " "
                        + request.query().parameterValue("lastname")))));
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
        final WebServer server = started(new WebServer(0)
                .page("/", (_, _) -> page("not static"))
                .staticResources(new StaticResources(tempDir.toFile(), "/res/")));
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
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("bundle")));
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
        final WebServer server = started(new WebServer(0).page("/head", (_, _) -> page("head body")));
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
    void all_parsed_methods_reach_the_page_application() throws Exception {
        final WebServer server = started(new WebServer(0).pageApplication(
                _ -> PageResult.live(page("delete"))));
        try {
            final java.net.http.HttpRequest delete = java.net.http.HttpRequest.newBuilder(uri(server, "/delete"))
                    .DELETE()
                    .build();

            final HttpResponse<String> response = client.send(delete, BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void render_failures_do_not_expose_exception_details() throws Exception {
        assertSanitizedRenderFailure(404, "404 Not Found", new PageNotFoundException(DIAGNOSTIC_CANARY));
        assertSanitizedRenderFailure(403, "403 Forbidden", new ComponentAccessDeniedException(DIAGNOSTIC_CANARY));
        assertSanitizedRenderFailure(500, "500 Internal Server Error", new RuntimeException(DIAGNOSTIC_CANARY));
    }

    @Test
    void rejects_non_positive_connection_limit() {
        assertThrows(IllegalArgumentException.class,
                () -> new WebServer(0)
                        .page("/", (_, _) -> page("limit"))
                        .connectionLimit(0));
    }

    @Test
    void upgrades_to_websocket_and_binds_live_page_session() throws Exception {
        final WebServer server = started(new WebServer(0).page("/live", (_, _) -> page("live")));
        try {
            client.send(get(server, "/live"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            final CompletableFuture<String> firstText = new CompletableFuture<>();

            final WebSocket webSocket = client.newWebSocketBuilder()
                    .buildAsync(webSocketUri(server, sessionId), new TestWebSocketListener(firstText, new CompletableFuture<>()))
                    .join();

            assertEquals("[17,1,[0,0]]", firstText.get(2, TimeUnit.SECONDS));
            assertTrue(server.pagesStorage.isEmpty());
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        } finally {
            server.stop();
        }
    }

    @Test
    void tracks_live_websocket_and_resumable_session_gauges() throws Exception {
        final MetricRegistry metrics = new MetricRegistry(MetricNames.frameworkCatalog());
        final WebServer server = started(new WebServer(0)
                .page("/gauges", (_, _) -> page("gauges"))
                .metrics(metrics));
        try {
            client.send(get(server, "/gauges"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            final CompletableFuture<String> firstText = new CompletableFuture<>();
            final WebSocket webSocket = client.newWebSocketBuilder()
                    .buildAsync(webSocketUri(server, sessionId),
                                new TestWebSocketListener(firstText, new CompletableFuture<>()))
                    .join();

            assertEquals("[17,1,[0,0]]", firstText.get(2, TimeUnit.SECONDS));
            awaitActiveWebSockets(server, 1);
            assertEquals(1, metrics.value(MetricNames.WEB_SOCKET_CONNECTIONS_ACTIVE));
            assertEquals(1, metrics.value(MetricNames.PAGE_SESSIONS_ACTIVE));
            assertEquals(2, metrics.value(MetricNames.HTTP_REQUESTS));
            assertEquals(1, metrics.activeMetricObjectCount());
            final var connectionMetrics = metrics.metricObjectSnapshots().getFirst();
            assertEquals(MetricObjectTypes.WEB_SOCKET_CONNECTION, connectionMetrics.type());
            assertTrue(connectionMetrics.metrics()
                    .value(MetricObjectTypes.WEB_SOCKET_MESSAGES_RECEIVED) >= 1);
            assertTrue(connectionMetrics.metrics()
                    .value(MetricObjectTypes.WEB_SOCKET_MESSAGES_SENT) >= 1);
            assertTrue(connectionMetrics.metrics()
                    .value(MetricObjectTypes.WEB_SOCKET_BYTES_RECEIVED) > 0);
            assertTrue(connectionMetrics.metrics()
                    .value(MetricObjectTypes.WEB_SOCKET_BYTES_SENT) > 0);

            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
            awaitActiveWebSockets(server, 0);
            assertEquals(0, metrics.value(MetricNames.WEB_SOCKET_CONNECTIONS_ACTIVE));
            assertEquals(1, metrics.value(MetricNames.PAGE_SESSIONS_ACTIVE));
            assertEquals(0, metrics.activeMetricObjectCount());

            server.stop();
            assertEquals(0, metrics.value(MetricNames.PAGE_SESSIONS_ACTIVE));
        } finally {
            server.stop();
        }
    }

    @Test
    void responds_to_websocket_ping_with_pong_payload() throws Exception {
        final WebServer server = started(new WebServer(0).page("/ping", (_, _) -> page("ping")));
        try {
            client.send(get(server, "/ping"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            final CompletableFuture<String> firstText = new CompletableFuture<>();
            final CompletableFuture<ByteBuffer> pong = new CompletableFuture<>();
            final WebSocket webSocket = client.newWebSocketBuilder()
                    .buildAsync(webSocketUri(server, sessionId), new TestWebSocketListener(firstText, pong))
                    .join();

            assertEquals("[17,1,[0,0]]", firstText.get(2, TimeUnit.SECONDS));
            webSocket.sendPing(ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8))).join();

            assertEquals("abc", StandardCharsets.UTF_8.decode(pong.get(2, TimeUnit.SECONDS)).toString());
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "").join();
        } finally {
            server.stop();
        }
    }

    @Test
    void stop_sends_going_away_close_to_active_websocket() throws Exception {
        final WebServer server = started(new WebServer(0).page("/stop", (_, _) -> page("stop")));
        try (Socket socket = new Socket("localhost", server.port())) {
            client.send(get(server, "/stop"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            writeHandshake(socket, server.port(), sessionId.deviceId(), sessionId.sessionId(), "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));
            sendClientText(socket, "[7,2,0]");
            readServerFrame(socket);
            awaitActiveWebSockets(server, 1);

            final CompletableFuture<Void> stopped = stopAsync(server);
            final RawServerFrame close = readFrameWithOpcode(socket, WebSocketTestFrame.OPCODE_CLOSE);

            assertEquals(WebSocketTestFrame.CLOSE_GOING_AWAY, closeCode(close));
            assertEquals(WebServer.WEB_SOCKET_SERVER_STOP_REASON, closeReason(close));

            socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_CLOSE, close.payload));
            socket.getOutputStream().flush();
            stopped.get(2, TimeUnit.SECONDS);
            assertEquals(0, server.activeWebSocketCount());
        } finally {
            server.stop();
        }
    }

    @Test
    void stop_force_closes_websocket_that_does_not_reply_to_close() throws Exception {
        final WebServer server = started(new WebServer(0)
                .page("/force-stop", (_, _) -> page("force stop")));
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout(3_000);
            client.send(get(server, "/force-stop"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            writeHandshake(socket, server.port(), sessionId.deviceId(), sessionId.sessionId(), "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));
            sendClientText(socket, "[7,2,0]");
            readServerFrame(socket);
            awaitActiveWebSockets(server, 1);

            final long startedAt = System.nanoTime();
            server.stop();
            final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(elapsedMs < WebServer.WEB_SOCKET_CLOSE_GRACE_TIMEOUT_MS + 2_000);
            assertEquals(0, server.activeWebSocketCount());
            assertEquals(WebSocketTestFrame.CLOSE_GOING_AWAY, readCloseCode(socket));
            assertEquals(-1, socket.getInputStream().read());
        } finally {
            server.stop();
        }
    }

    @Test
    void client_close_deregisters_active_websocket() throws Exception {
        final WebServer server = started(new WebServer(0)
                .page("/client-close", (_, _) -> page("client close")));
        try (Socket socket = new Socket("localhost", server.port())) {
            client.send(get(server, "/client-close"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            writeHandshake(socket, server.port(), sessionId.deviceId(), sessionId.sessionId(), "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));
            sendClientText(socket, "[7,2,0]");
            readServerFrame(socket);
            awaitActiveWebSockets(server, 1);

            socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_CLOSE,
                                                             WebSocketTestFrame.closePayload(WebSocketTestFrame.CLOSE_NORMAL, "")));
            socket.getOutputStream().flush();

            assertEquals(WebSocketTestFrame.CLOSE_NORMAL, readCloseCode(socket));
            awaitActiveWebSockets(server, 0);
        } finally {
            server.stop();
        }
    }

    @Test
    void stop_clears_pending_rendered_pages() throws Exception {
        final WebServer server = started(new WebServer(0).page("/pending", (_, _) -> page("pending")));
        try {
            client.send(get(server, "/pending"), BodyHandlers.ofString());
            assertFalse(server.pagesStorage.isEmpty());

            server.stop();

            assertTrue(server.pagesStorage.isEmpty());
        } finally {
            server.stop();
        }
    }

    @Test
    void reconnect_resumes_the_same_local_page_session() throws Exception {
        final WebServer server = started(new WebServer(0).page("/resume", (_, _) -> page("resume")));
        try {
            client.send(get(server, "/resume"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();

            try (Socket firstSocket = new Socket("localhost", server.port())) {
                writeHandshake(firstSocket,
                               server.port(),
                               sessionId.deviceId(),
                               sessionId.sessionId(),
                               "dGhlIHNhbXBsZSBub25jZQ==");
                assertTrue(readHttpHeaders(firstSocket).startsWith("HTTP/1.1 101 Switching Protocols"));
                sendClientText(firstSocket, "[7,2,0]");
                assertEquals("[17,1,[0,0]]", text(readServerFrame(firstSocket)));
                assertEquals("[18,1]", text(readServerFrame(firstSocket)));
                sendClientText(firstSocket, "[8,1]");
                sendClientClose(firstSocket, WebSocketTestFrame.CLOSE_NORMAL, "");
                assertEquals(WebSocketTestFrame.CLOSE_NORMAL, readCloseCode(firstSocket));
            }

            awaitActiveWebSockets(server, 0);
            assertEquals(1, server.liveSessionCount());

            try (Socket resumedSocket = new Socket("localhost", server.port())) {
                writeHandshake(resumedSocket,
                               server.port(),
                               sessionId.deviceId(),
                               sessionId.sessionId(),
                               "dGhlIHNhbXBsZSBub25jZQ==");
                assertTrue(readHttpHeaders(resumedSocket).startsWith("HTTP/1.1 101 Switching Protocols"));
                sendClientText(resumedSocket, "[7,2,1]");
                assertEquals("[18,1]", text(readServerFrame(resumedSocket)));
                assertEquals(1, server.liveSessionCount());

                sendClientText(resumedSocket, "[9]");
                final RawServerFrame close = readFrameWithOpcode(resumedSocket, WebSocketTestFrame.OPCODE_CLOSE);
                sendClientClosePayload(resumedSocket, close.payload);
            }

            awaitActiveWebSockets(server, 0);
            assertEquals(0, server.liveSessionCount());
        } finally {
            server.stop();
        }
    }

    @Test
    void websocket_handshake_returns_rfc_accept_key() throws Exception {
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("handshake")));
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
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("bad key")));
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
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("unknown ws")));
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
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("missing headers")));
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
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("unmasked")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-unmasked", "session-unmasked", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(new byte[] {(byte) 0x81, 0x00});
            socket.getOutputStream().flush();

            assertEquals(WebSocketTestFrame.CLOSE_PROTOCOL_ERROR, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    @Test
    void invalid_utf8_websocket_text_closes_with_invalid_payload() throws Exception {
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("utf8")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-utf8", "session-utf8", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_TEXT, new byte[] {(byte) 0xC3, 0x28}));
            socket.getOutputStream().flush();

            assertEquals(WebSocketTestFrame.CLOSE_INVALID_PAYLOAD, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    @Test
    void fragmented_websocket_text_message_is_reassembled() throws Exception {
        final WebServer server = started(new WebServer(0)
                .page("/fragmented", (_, _) -> page("fragmented")));
        try (Socket socket = new Socket("localhost", server.port())) {
            client.send(get(server, "/fragmented"), BodyHandlers.ofString());
            final rsp.page.QualifiedSessionId sessionId = server.pagesStorage.keySet().iterator().next();
            writeHandshake(socket, server.port(), sessionId.deviceId(), sessionId.sessionId(), "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));
            sendClientText(socket, "[7,2,0]");
            readServerFrame(socket);

            socket.getOutputStream().write(maskedClientFrame(false, WebSocketTestFrame.OPCODE_TEXT, "[".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().write(maskedClientFrame(true, WebSocketTestFrame.OPCODE_CONTINUATION, "6]".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_PING, "ok".getBytes(StandardCharsets.UTF_8)));
            socket.getOutputStream().flush();

            final RawServerFrame pong = readFrameWithOpcode(socket, WebSocketTestFrame.OPCODE_PONG);
            assertEquals("ok", new String(pong.payload, StandardCharsets.UTF_8));
        } finally {
            server.stop();
        }
    }

    @Test
    void binary_websocket_message_closes_with_unsupported_data() throws Exception {
        final WebServer server = started(new WebServer(0).page("/", (_, _) -> page("binary")));
        try (Socket socket = new Socket("localhost", server.port())) {
            writeHandshake(socket, server.port(), "device-binary", "session-binary", "dGhlIHNhbXBsZSBub25jZQ==");
            assertTrue(readHttpHeaders(socket).startsWith("HTTP/1.1 101 Switching Protocols"));

            socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_BINARY, new byte[] {1, 2, 3}));
            socket.getOutputStream().flush();

            assertEquals(WebSocketTestFrame.CLOSE_UNSUPPORTED_DATA, readCloseCode(socket));
        } finally {
            server.stop();
        }
    }

    private static WebServer started(final WebServer server) {
        server.start();
        return server;
    }

    private void assertSanitizedRenderFailure(final int expectedStatus,
                                              final String expectedBody,
                                              final RuntimeException failure) throws Exception {
        final WebServer server = started(new WebServer(0)
                .page("/failure", (_, _) -> failingPage(failure)));
        try {
            final HttpResponse<String> response = client.send(get(server, "/failure"), BodyHandlers.ofString());

            assertEquals(expectedStatus, response.statusCode());
            assertEquals(expectedBody, response.body());
            assertFalse(response.body().contains(DIAGNOSTIC_CANARY));
        } finally {
            server.stop();
        }
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

    private static void sendClientText(final Socket socket, final String message) throws Exception {
        socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_TEXT,
                                                         message.getBytes(StandardCharsets.UTF_8)));
        socket.getOutputStream().flush();
    }

    private static void sendClientClose(final Socket socket, final int code, final String reason) throws Exception {
        sendClientClosePayload(socket, WebSocketTestFrame.closePayload(code, reason));
    }

    private static void sendClientClosePayload(final Socket socket, final byte[] payload) throws Exception {
        socket.getOutputStream().write(maskedClientFrame(WebSocketTestFrame.OPCODE_CLOSE, payload));
        socket.getOutputStream().flush();
    }

    private static String text(final RawServerFrame frame) {
        return new String(frame.payload, StandardCharsets.UTF_8);
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
            if (frame.opcode == WebSocketTestFrame.OPCODE_CLOSE) {
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
        if (first < 0 || second < 0) {
            throw new AssertionError("Unexpected end of WebSocket stream");
        }
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

    private static int closeCode(final RawServerFrame frame) {
        assertTrue(frame.payload.length >= 2);
        return ((frame.payload[0] & 0xFF) << 8) | (frame.payload[1] & 0xFF);
    }

    private static String closeReason(final RawServerFrame frame) {
        if (frame.payload.length <= 2) {
            return "";
        }
        return new String(frame.payload, 2, frame.payload.length - 2, StandardCharsets.UTF_8);
    }

    private static CompletableFuture<Void> stopAsync(final WebServer server) {
        final CompletableFuture<Void> stopped = new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                server.stop();
                stopped.complete(null);
            } catch (final Throwable ex) {
                stopped.completeExceptionally(ex);
            }
        });
        return stopped;
    }

    private static void awaitActiveWebSockets(final WebServer server,
                                              final int expected) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (server.activeWebSocketCount() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, server.activeWebSocketCount());
    }

    private static Component<?, ?> page(final String text) {
        return new StatelessComponent((rsp.component.View<Unit>) _ -> html(head(title("HTTP test")),
                                                                           body(h1(text), p("served"))));
    }

    private static Component<?, ?> failingPage(final RuntimeException failure) {
        return new StatelessComponent((rsp.component.View<Unit>) _ -> _ -> {
            throw failure;
        });
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
            webSocket.sendText("[7,2,0]", true);
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
