package rsp.server;

import java.util.Optional;
import java.util.function.Function;

public class HttpRequest {
    public final String path;
    public final Function<String, Optional<String>> getParam;
    public final Function<String, Optional<String>> getCookie;

    public HttpRequest(String path,
                       Function<String, Optional<String>> param,
                       Function<String, Optional<String>> cookie) {
        this.path = path;
        this.getParam = param;
        this.getCookie = cookie;
    }
}
