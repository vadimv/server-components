package rsp.http;

import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;

import java.util.Locale;

record ParsedHttpRequest(HttpRequest request, String version) {
    HttpMethod method() {
        return request.method;
    }

    boolean isWebSocketUpgrade() {
        final String upgrade = request.header("Upgrade");
        final String connection = request.header("Connection");
        return upgrade != null
               && "websocket".equals(upgrade.toLowerCase(Locale.ROOT))
               && connection != null
               && connection.toLowerCase(Locale.ROOT).contains("upgrade");
    }
}
