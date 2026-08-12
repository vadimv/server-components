package rsp.http;

import rsp.server.http.Header;
import rsp.server.http.HttpResponse;

import java.util.List;

final class HttpResponses {
    private HttpResponses() {
    }

    static HttpResponse text(final int status, final String body) {
        return new HttpResponse(status,
                                List.of(new Header("Content-Type", "text/plain; charset=utf-8")),
                                body);
    }
}
