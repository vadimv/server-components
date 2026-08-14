package rsp.http;

import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class WebSocketUpgrader {
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    boolean isWebSocketRequest(final HttpRequest request) {
        final String upgrade = request.header("Upgrade");
        return upgrade != null && "websocket".equals(upgrade.toLowerCase(Locale.ROOT));
    }

    Optional<String> upgrade(final Socket socket,
                             final ParsedHttpRequest parsedRequest,
                             final List<String> supportedSubprotocols) throws IOException, WebSocketHandshakeException {
        final HttpRequest request = parsedRequest.request();
        if (request.method != HttpMethod.GET) {
            throw new WebSocketHandshakeException(405, "Method Not Allowed");
        }
        if (!"HTTP/1.1".equals(parsedRequest.version())) {
            throw new WebSocketHandshakeException(400, "WebSocket upgrade requires HTTP/1.1");
        }
        validateUpgradeHeaders(request);
        final Optional<String> subprotocol = negotiateSubprotocol(request.header("Sec-WebSocket-Protocol"),
                                                                  supportedSubprotocols);
        writeSwitchingProtocols(socket.getOutputStream(),
                                acceptKey(request.header("Sec-WebSocket-Key")),
                                subprotocol);
        return subprotocol;
    }

    private void validateUpgradeHeaders(final HttpRequest request) throws WebSocketHandshakeException {
        final String upgrade = request.header("Upgrade");
        if (upgrade == null || !"websocket".equals(upgrade.toLowerCase(Locale.ROOT))) {
            throw new WebSocketHandshakeException(400, "Invalid WebSocket Upgrade header");
        }
        if (!containsToken(request.header("Connection"), "upgrade")) {
            throw new WebSocketHandshakeException(400, "Invalid WebSocket Connection header");
        }
        if (!"13".equals(request.header("Sec-WebSocket-Version"))) {
            throw new WebSocketHandshakeException(400, "Unsupported WebSocket version");
        }
        final String key = request.header("Sec-WebSocket-Key");
        if (key == null || !isValidKey(key)) {
            throw new WebSocketHandshakeException(400, "Invalid Sec-WebSocket-Key");
        }
    }

    private boolean isValidKey(final String key) {
        try {
            return Base64.getDecoder().decode(key.trim()).length == 16;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean containsToken(final String headerValue, final String token) {
        if (headerValue == null) {
            return false;
        }
        for (final String value : headerValue.split(",")) {
            if (token.equals(value.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> negotiateSubprotocol(final String requestedHeader,
                                                  final List<String> supportedSubprotocols) {
        if (requestedHeader == null || supportedSubprotocols.isEmpty()) {
            return Optional.empty();
        }
        for (final String requested : requestedHeader.split(",")) {
            final String token = requested.trim();
            if (supportedSubprotocols.contains(token)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    private String acceptKey(final String key) {
        Objects.requireNonNull(key);
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            final byte[] digest = sha1.digest((key.trim() + WEBSOCKET_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void writeSwitchingProtocols(final OutputStream output,
                                         final String acceptKey,
                                         final Optional<String> subprotocol) throws IOException {
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
