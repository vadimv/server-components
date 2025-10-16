package rsp.javax.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.server.HandshakeRequest;
import rsp.server.http.HttpRequest;
import rsp.server.Path;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

public final class HttpRequestUtils {

    private HttpRequestUtils() {}

    /**
     * Creates a new instance of {@link HttpRequest} given a Servlet API HttpServletRequest
     * @param request a HTTP Servlet request
     * @return a HTTP request
     */
    public static HttpRequest httpRequest(final HttpServletRequest request) {
        return new HttpRequest(httpMethod(request.getMethod()),
                               stringToURI(request.getRequestURI()),
                               request.getRequestURL().toString(),
                               Path.of(request.getPathInfo()),
                               s -> Optional.ofNullable(request.getParameter(s)),
                               h -> Optional.ofNullable(request.getHeader(h)));
    }

    /**
     * Creates a new instance of {@link HttpRequest} given a JSR 356 WebSocket handshake request
     * @param handshakeRequest a WebSocket handshake request
     * @return a HTTP request
     */
    public static HttpRequest httpRequest(final HandshakeRequest handshakeRequest) {
        return new HttpRequest(HttpRequest.HttpMethod.GET,
                               handshakeRequest.getRequestURI(),
                               handshakeRequest.getRequestURI().toString(),
                               Path.of(handshakeRequest.getRequestURI().getPath()),
                               name ->  Optional.ofNullable(handshakeRequest.getParameterMap().get(name)).map(val -> val.get(0)),
                               name -> Optional.ofNullable(handshakeRequest.getHeaders().get(name)).map(val -> val.get(0)));
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
