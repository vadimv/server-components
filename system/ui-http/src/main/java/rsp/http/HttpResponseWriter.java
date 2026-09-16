package rsp.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class HttpResponseWriter {
    private static final int BUFFER_SIZE = 8192;

    void write(final OutputStream output,
               final HttpResponse response,
               final HttpMethod requestMethod) throws IOException {
        output.write(("HTTP/1.1 " + response.status().code() + " " + response.status().reasonPhrase() + "\r\n")
                             .getBytes(StandardCharsets.ISO_8859_1));
        boolean hasConnection = false;
        boolean hasContentLength = false;
        for (final HttpHeader header : response.headers()) {
            if ("connection".equals(header.name().toLowerCase(Locale.ROOT))) {
                hasConnection = true;
            }
            if ("content-length".equals(header.name().toLowerCase(Locale.ROOT))) {
                hasContentLength = true;
            }
            output.write((header.name() + ": " + header.value() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        if (!hasContentLength && response.body().contentLength().isPresent()) {
            output.write(("Content-Length: " + response.body().contentLength().getAsLong() + "\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        if (!hasConnection) {
            output.write("Connection: close\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));

        try (InputStream body = response.body().openStream()) {
            if (requestMethod == HttpMethod.HEAD) {
                output.flush();
                return;
            }
            final byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = body.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

}
