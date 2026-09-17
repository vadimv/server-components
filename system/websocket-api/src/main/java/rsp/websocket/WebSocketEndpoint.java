package rsp.websocket;

import rsp.http.HttpRequest;

import java.util.List;

/** UI-independent selection and lifecycle contract for one WebSocket endpoint. */
public interface WebSocketEndpoint {
    boolean matches(HttpRequest request);

    default void validate(HttpRequest request) throws WebSocketHandshakeException {
    }

    default List<String> supportedSubprotocols() {
        return List.of();
    }

    WebSocketListener open(HttpRequest request, WebSocketSession session);
}
