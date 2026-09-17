package rsp.server.jdk;

import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.websocket.WebSocketHandshakeException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class WebSocketUpgrader {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    boolean isWebSocketRequest(HttpRequest request) {
        String upgrade = request.header("Upgrade");
        return upgrade != null && "websocket".equals(upgrade.toLowerCase(Locale.ROOT));
    }

    void upgrade(Socket socket, ParsedHttpRequest parsedRequest, List<String> supportedSubprotocols)
            throws IOException, WebSocketHandshakeException {
        HttpRequest request = parsedRequest.request();
        if (request.method() != HttpMethod.GET) {
            throw new WebSocketHandshakeException(405, "Method Not Allowed");
        }
        if (!"HTTP/1.1".equals(parsedRequest.version())) {
            throw new WebSocketHandshakeException(400, "WebSocket upgrade requires HTTP/1.1");
        }
        validateUpgradeHeaders(request);
        Optional<String> subprotocol = negotiateSubprotocol(request.header("Sec-WebSocket-Protocol"),
                supportedSubprotocols);
        writeSwitchingProtocols(socket.getOutputStream(), acceptKey(request.header("Sec-WebSocket-Key")),
                subprotocol);
    }

    private void validateUpgradeHeaders(HttpRequest request) throws WebSocketHandshakeException {
        String upgrade = request.header("Upgrade");
        if (upgrade == null || !"websocket".equals(upgrade.toLowerCase(Locale.ROOT))) {
            throw new WebSocketHandshakeException(400, "Invalid WebSocket Upgrade header");
        }
        if (!containsToken(request.header("Connection"), "upgrade")) {
            throw new WebSocketHandshakeException(400, "Invalid WebSocket Connection header");
        }
        if (!"13".equals(request.header("Sec-WebSocket-Version"))) {
            throw new WebSocketHandshakeException(400, "Unsupported WebSocket version");
        }
        String key = request.header("Sec-WebSocket-Key");
        if (key == null || !isValidKey(key)) {
            throw new WebSocketHandshakeException(400, "Invalid Sec-WebSocket-Key");
        }
    }

    private boolean isValidKey(String key) {
        try {
            return Base64.getDecoder().decode(key.trim()).length == 16;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    private boolean containsToken(String headerValue, String token) {
        if (headerValue == null) {
            return false;
        }
        for (String value : headerValue.split(",")) {
            if (token.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> negotiateSubprotocol(String requestedHeader, List<String> supportedSubprotocols) {
        if (requestedHeader == null || supportedSubprotocols.isEmpty()) {
            return Optional.empty();
        }
        for (String requested : requestedHeader.split(",")) {
            String token = requested.trim();
            if (supportedSubprotocols.contains(token)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    private String acceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key.trim() + WEBSOCKET_GUID)
                    .getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void writeSwitchingProtocols(OutputStream output, String acceptKey, Optional<String> subprotocol)
            throws IOException {
        output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        if (subprotocol.isPresent()) {
            output.write(("Sec-WebSocket-Protocol: " + subprotocol.get() + "\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }
}
