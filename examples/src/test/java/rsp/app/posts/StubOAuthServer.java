package rsp.app.posts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Minimal OAuth 2.0 PKCE-compatible server for E2E smoke tests.
 * Auto-approves all authorization requests (no login UI).
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code /authorize} — redirects to callback with authorization code</li>
 *   <li>{@code /token} — exchanges code + code_verifier for access token</li>
 *   <li>{@code /userinfo} — returns user info JSON for valid Bearer token</li>
 * </ul>
 */
class StubOAuthServer {

    private static final String TEST_USERNAME = "testuser";
    private static final String ACCESS_TOKEN = "stub-access-token";

    private final HttpServer server;
    private final int port;
    private final AtomicBoolean rejectNext = new AtomicBoolean(false);

    // code → code_challenge (stored at /authorize, verified at /token)
    private final Map<String, String> pendingCodes = new ConcurrentHashMap<>();

    StubOAuthServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/authorize", this::handleAuthorize);
        server.createContext("/token", this::handleToken);
        server.createContext("/userinfo", this::handleUserInfo);
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    /**
     * Makes the next /authorize request return an error instead of a code.
     */
    void rejectNext() {
        rejectNext.set(true);
    }

    int port() {
        return port;
    }

    private void handleAuthorize(HttpExchange exchange) throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
        String redirectUri = params.get("redirect_uri");
        String state = params.get("state");
        String codeChallenge = params.get("code_challenge");

        if (rejectNext.compareAndSet(true, false)) {
            // Simulate authorization denied
            String location = redirectUri + "?error=access_denied&state=" + state;
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        // Auto-approve: generate code and redirect back
        String code = "authcode-" + System.nanoTime();
        pendingCodes.put(code, codeChallenge);

        String location = redirectUri + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&state=" + state;
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void handleToken(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseFormBody(body);

        String code = params.get("code");
        String codeVerifier = params.get("code_verifier");

        String storedChallenge = pendingCodes.remove(code);
        if (storedChallenge == null) {
            sendJson(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }

        // Verify PKCE: SHA-256(code_verifier) must match stored code_challenge
        try {
            String computedChallenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));
            if (!computedChallenge.equals(storedChallenge)) {
                sendJson(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"PKCE verification failed\"}");
                return;
            }
        } catch (NoSuchAlgorithmException e) {
            sendJson(exchange, 500, "{\"error\":\"server_error\"}");
            return;
        }

        sendJson(exchange, 200, "{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\"}");
    }

    private void handleUserInfo(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.equals("Bearer " + ACCESS_TOKEN)) {
            sendJson(exchange, 401, "{\"error\":\"invalid_token\"}");
            return;
        }
        sendJson(exchange, 200, "{\"preferred_username\":\"" + TEST_USERNAME + "\"}");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) return Map.of();
        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .collect(Collectors.toMap(
                        a -> URLDecoder.decode(a[0], StandardCharsets.UTF_8),
                        a -> a.length > 1 ? URLDecoder.decode(a[1], StandardCharsets.UTF_8) : ""));
    }

    private static Map<String, String> parseFormBody(String body) {
        return parseQuery(body);
    }
}
