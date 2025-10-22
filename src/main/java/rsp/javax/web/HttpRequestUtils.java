package rsp.javax.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.server.HandshakeRequest;
import rsp.server.http.Header;
import rsp.server.http.HttpRequest;
import rsp.server.Path;
import rsp.server.http.Query;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public final class HttpRequestUtils {

    private HttpRequestUtils() {}

    /**
     * Creates a new instance of {@link HttpRequest} given a Servlet API HttpServletRequest
     * @param request a HTTP Servlet request
     * @return a HTTP request
     */
    public static HttpRequest httpRequest(final HttpServletRequest request) {
        final Map<String, String[]> parameterMap = request.getParameterMap();
        final List<Query.Parameter> parameters = new ArrayList<>();
        for (final Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            parameters.add(new Query.Parameter(entry.getKey(), entry.getValue()[0])); // TODO support parameter arrays
        }
        final List<Header> headers = new ArrayList<>();
        for (String headerName : Collections.list(request.getHeaderNames()) ) {
            headers.add(new Header(headerName, request.getHeader(headerName)));
        }
        return new HttpRequest(httpMethod(request.getMethod()),
                               stringToURI(request.getRequestURI()),
                               request.getRequestURL().toString(),
                               Path.of(request.getPathInfo()),
                               new Query(parameters),
                               headers);
    }

    /**
     * Creates a new instance of {@link HttpRequest} given a JSR 356 WebSocket handshake request
     * @param handshakeRequest a WebSocket handshake request
     * @return a HTTP request
     */
    public static HttpRequest httpRequest(final HandshakeRequest handshakeRequest) {
        final Map<String, List<String>> parameterMap = handshakeRequest.getParameterMap();
        final List<Query.Parameter> parameters = new ArrayList<>();
        for (final Map.Entry<String, List<String>> entry : parameterMap.entrySet()) {
            parameters.add(new Query.Parameter(entry.getKey(), entry.getValue().get(0))); // TODO support parameter arrays
        }
        final List<Header> headers = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry: handshakeRequest.getHeaders().entrySet()) {
            headers.add(new Header(entry.getKey(), entry.getValue().get(0)));
        }
        return new HttpRequest(HttpRequest.HttpMethod.GET,
                               handshakeRequest.getRequestURI(),
                               handshakeRequest.getRequestURI().toString(),
                               Path.of(handshakeRequest.getRequestURI().getPath()),
                               new Query(parameters),
                               headers);
    }

    private static URI stringToURI(final String str) {
        try {
            return new URI(str);
        } catch (final URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static HttpRequest.HttpMethod httpMethod(final String method) {
        try {
            return HttpRequest.HttpMethod.valueOf(method);
        } catch (final IllegalArgumentException ex) {
            throw new RuntimeException("Unsupported HTTP method: " + method, ex);
        }
    }
}
