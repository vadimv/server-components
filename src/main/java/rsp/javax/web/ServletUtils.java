package rsp.javax.web;

import rsp.server.HttpRequest;
import rsp.server.Path;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.HandshakeRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

public final class ServletUtils {

    public static HttpRequest httpRequest(HttpServletRequest request) {
        return new HttpRequest(stringToURI(request.getRequestURI()),
                Path.of(request.getPathInfo()),
                s -> Optional.ofNullable(request.getParameter(s)),
                h -> Optional.ofNullable(request.getHeader(h)),
                n -> ServletUtils.cookie(request, n).map(c -> c.getValue()));
    }

    public static HttpRequest httpRequest(HandshakeRequest handshakeRequest) {
        return new HttpRequest(handshakeRequest.getRequestURI(),
                Path.of(handshakeRequest.getRequestURI().getPath()),
                name ->  Optional.ofNullable(handshakeRequest.getParameterMap().get(name)).map(val -> val.get(0)),
                name -> Optional.ofNullable(handshakeRequest.getHeaders().get(name)).map(val -> val.get(0)),
                name -> Optional.empty());
    }

    private static URI stringToURI(String str) {
        try {
            return new URI(str);
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Optional<Cookie> cookie(HttpServletRequest request, String name) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(name);
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (int i=0;i< cookies.length;i++) {
                if (name.equals(cookies[i].getName())){
                    return Optional.of(cookies[i]);
                }
            }
        }
        return Optional.empty();
    }
}
