package rsp.http;

import rsp.server.http.Header;
import rsp.server.http.HttpMethod;
import rsp.server.http.HttpResponse;

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
        output.write(("HTTP/1.1 " + response.status + " " + reasonPhrase(response.status) + "\r\n")
                             .getBytes(StandardCharsets.ISO_8859_1));
        boolean hasConnection = false;
        for (final Header header : response.headers) {
            if ("connection".equals(header.name().toLowerCase(Locale.ROOT))) {
                hasConnection = true;
            }
            output.write((header.name() + ": " + header.value() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        if (!hasConnection) {
            output.write("Connection: close\r\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));

        try (InputStream body = response.bodyStream) {
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

    private static String reasonPhrase(final int status) {
        return switch (status) {
            case 200 -> "OK";
            case 302 -> "Found";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 408 -> "Request Timeout";
            case 413 -> "Payload Too Large";
            case 414 -> "URI Too Long";
            case 431 -> "Request Header Fields Too Large";
            case 500 -> "Internal Server Error";
            case 501 -> "Not Implemented";
            default -> "";
        };
    }
}
