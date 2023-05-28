package rsp.server;

import rsp.util.data.Tuple2;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public final class HttpResponse {
    public final int status;
    public final List<Tuple2<String,String>> headers;
    public final InputStream bodyStream;

    public HttpResponse(final int status,
                        final List<Tuple2<String,String>> headers,
                        final InputStream bodyStream) {
        this.status = status;
        this.headers = headers;
        this.bodyStream = bodyStream;
    }

    public HttpResponse(final int status,
                        final List<Tuple2<String,String>> headers,
                        final String body) {
        this(status, headers, new ByteArrayInputStream(body.getBytes()));
    }
}
