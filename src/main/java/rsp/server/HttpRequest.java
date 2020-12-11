package rsp.server;

import rsp.javax.web.ServletUtils;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.HandshakeRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public final class HttpRequest {
    public final URI uri;
    public final Path path;
    public final Function<String, Optional<String>> getParam;
    public final Function<String, Optional<String>> getHeader;
    public final Function<String, Optional<String>> getCookie;
    public HttpRequest(URI uri,
                       Path path,
                       Function<String, Optional<String>> param,
                       Function<String, Optional<String>> getHeader,
                       Function<String, Optional<String>> getCookie) {
        this.uri = uri;
        this.path = path;
        this.getParam = param;
        this.getHeader = getHeader;
        this.getCookie = getCookie;
    }

    public static HttpRequest of(HttpServletRequest request) {
        return new HttpRequest(stringToURI(request.getRequestURI()),
                               Path.of(request.getPathInfo()),
                               s -> Optional.ofNullable(request.getParameter(s)),
                               h -> Optional.ofNullable(request.getHeader(h)),
                               n -> ServletUtils.cookie(request, n).map(c -> c.getValue()));
    }

    public static HttpRequest of(HandshakeRequest handshakeRequest) {
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

    public Function<String, Optional<String>> getCookie() {
        return name -> getHeader.apply("Cookie").flatMap(headerValue ->
                Arrays.stream(headerValue.split(";"))
                        .map(String::trim)
                        .map(pairStr -> Arrays.stream(pairStr.split("=")).toArray(String[]::new))
                        .filter(pair -> pair.length == 2 && pair[0].equals(name)).findFirst()
                        .map(pair -> pair[1]));
    }
}
