package rsp.server;

import rsp.util.Tuple2;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

public class HttpResponse {
    public final int status;
    public final List<Tuple2<String,String>> headers;
    public final InputStream bodyStream;

    public HttpResponse(int status,
                        List<Tuple2<String,String>> headers,
                        InputStream bodyStream) {
        this.status = status;
        this.headers = headers;
        this.bodyStream = bodyStream;
    }

    public HttpResponse(int status,
                        List<Tuple2<String,String>> headers,
                        String body) {
        this(status, headers, new ByteArrayInputStream(body.getBytes()));
    }
}
