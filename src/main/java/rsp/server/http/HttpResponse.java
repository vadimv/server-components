package rsp.server.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Represents a HTTP response.
 */
public final class HttpResponse {

    public static final int OK_STATUS_CODE = 200;
    public static final int MOVED_TEMPORARILY_STATUS_CODE = 302;

    public final int status;
    public final List<Header> headers;
    public final InputStream bodyStream;

    public HttpResponse(final int status,
                        final List<Header> headers,
                        final InputStream bodyStream) {
        this.status = status;
        this.headers = headers;
        this.bodyStream = bodyStream;
    }

    public HttpResponse(final int status,
                        final List<Header> headers,
                        final String body) {
        this(status, headers, new ByteArrayInputStream(body.getBytes()));
    }
}
