package rsp.server;

import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.HandshakeRequest;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public class HttpRequest {
    public final String path;
    public final Function<String, Optional<String>> getParam;
    public final Function<String, Optional<String>> getHeader;

    public HttpRequest(String path,
                       Function<String, Optional<String>> param,
                       Function<String, Optional<String>> getHeader) {
        this.path = path;
        this.getParam = param;
        this.getHeader = getHeader;
    }

    public static HttpRequest of(HttpServletRequest request) {
        return new HttpRequest(request.getPathInfo(),
                s -> Optional.ofNullable(request.getParameter(s)),
                h -> Optional.ofNullable(request.getHeader(h)));
    }

    public static HttpRequest of(HandshakeRequest handshakeRequest) {
        return new HttpRequest(handshakeRequest.getRequestURI().getPath(),
                name ->  Optional.ofNullable(handshakeRequest.getParameterMap().get(name)).map(val -> val.get(0)),
                name -> Optional.ofNullable(handshakeRequest.getHeaders().get(name)).map(val -> val.get(0)));
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
