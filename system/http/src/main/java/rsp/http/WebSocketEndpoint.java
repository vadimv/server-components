package rsp.http;

import rsp.server.http.HttpRequest;

import java.util.List;

interface WebSocketEndpoint {
    boolean matches(HttpRequest request);

    default void validate(HttpRequest request) throws WebSocketHandshakeException {
    }

    default List<String> supportedSubprotocols() {
        return List.of();
    }

    WebSocketListener open(HttpRequest request, WebSocketSession session);
}
