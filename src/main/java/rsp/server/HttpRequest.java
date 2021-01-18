package rsp.server;

import rsp.page.PageRendering;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public final class HttpRequest {
    public final URI uri;
    public final Path path;
    public final Function<String, Optional<String>> getParam;
    public final Function<String, Optional<String>> getHeader;

    public HttpRequest(URI uri,
                       Path path,
                       Function<String, Optional<String>> param,
                       Function<String, Optional<String>> getHeader) {
        this.uri = uri;
        this.path = path;
        this.getParam = param;
        this.getHeader = getHeader;
    }

    public Function<String, Optional<String>> getCookie() {
        return name -> getHeader.apply("Cookie").flatMap(headerValue ->
                Arrays.stream(headerValue.split(";"))
                        .map(String::trim)
                        .map(pairStr -> Arrays.stream(pairStr.split("=")).toArray(String[]::new))
                        .filter(pair -> pair.length == 2 && pair[0].equals(name)).findFirst()
                        .map(pair -> pair[1]));
    }

    public Optional<String> deviceId() {
        return getCookie().apply(PageRendering.DEVICE_ID_COOKIE_NAME);
    }
}
