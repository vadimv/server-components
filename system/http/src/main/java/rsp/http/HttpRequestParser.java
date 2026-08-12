package rsp.http;

import rsp.server.Path;
import rsp.server.http.Header;
import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;
import rsp.server.http.Query;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class HttpRequestParser {
    static final int HEADER_READ_TIMEOUT_MS = 5_000;
    private static final int BODY_READ_TIMEOUT_MS = 10_000;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_REQUEST_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADER_COUNT = 100;
    private static final int MAX_FORM_BODY_BYTES = 256 * 1024;

    Optional<ParsedHttpRequest> parse(final Socket socket, final String scheme) throws IOException, HttpProtocolException {
        final InputStream input = socket.getInputStream();
        final byte[] headerBytes = readHeaderBytes(input);
        if (headerBytes.length == 0) {
            return Optional.empty();
        }
        // Headers are byte-oriented and use ISO-8859-1-compatible framing.
        final String headerBlock = new String(headerBytes, StandardCharsets.ISO_8859_1);
        final String[] lines = headerBlock.substring(0, headerBlock.length() - 4).split("\r\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        if (lines[0].getBytes(StandardCharsets.ISO_8859_1).length > MAX_REQUEST_LINE_BYTES) {
            throw new HttpProtocolException(414, "URI Too Long");
        }

        final RequestLine requestLine = parseRequestLine(lines[0]);
        final List<Header> headers = parseHeaders(lines);
        final int contentLength = contentLength(headers);
        byte[] body = new byte[0];
        if (contentLength > 0) {
            // This slice only needs bounded form bodies; generic request streaming belongs in a later slice.
            if (contentLength > MAX_FORM_BODY_BYTES) {
                throw new HttpProtocolException(413, "Payload Too Large");
            }
            socket.setSoTimeout(BODY_READ_TIMEOUT_MS);
            body = input.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new HttpProtocolException(400, "Bad Request");
            }
        }

        final URI requestUri = requestUri(requestLine.target());
        final String rawQuery = requestUri.getRawQuery();
        final Query query = mergeQueryAndForm(rawQuery, headers, body);
        final String path = requestUri.getPath() == null || requestUri.getPath().isEmpty() ? "/" : requestUri.getPath();
        final String url = absoluteUrl(scheme, requestLine.target(), requestUri, headers);
        final HttpRequest request = new HttpRequest(requestLine.method(),
                                                    requestUri,
                                                    url,
                                                    Path.of(path),
                                                    query,
                                                    headers);
        return Optional.of(new ParsedHttpRequest(request, requestLine.version()));
    }

    private byte[] readHeaderBytes(final InputStream input) throws IOException, HttpProtocolException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b1 = -1;
        int b2 = -1;
        int b3 = -1;
        int b4 = -1;
        while (buffer.size() <= MAX_HEADER_BYTES) {
            final int next;
            try {
                next = input.read();
            } catch (final SocketTimeoutException ex) {
                throw new HttpProtocolException(408, "Request Timeout");
            }
            if (next < 0) {
                if (buffer.size() == 0) {
                    return new byte[0];
                }
                throw new HttpProtocolException(400, "Bad Request");
            }
            buffer.write(next);
            // Keep a rolling four-byte window so CRLFCRLF can be detected while reading one byte at a time.
            b1 = b2;
            b2 = b3;
            b3 = b4;
            b4 = next;
            if (b1 == '\r' && b2 == '\n' && b3 == '\r' && b4 == '\n') {
                return buffer.toByteArray();
            }
        }
        throw new HttpProtocolException(431, "Request Header Fields Too Large");
    }

    private RequestLine parseRequestLine(final String line) throws HttpProtocolException {
        final String[] tokens = line.split(" +", 3);
        if (tokens.length != 3) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        final HttpMethod method;
        try {
            method = HttpMethod.valueOf(tokens[0]);
        } catch (final IllegalArgumentException ex) {
            throw new HttpProtocolException(501, "Not Implemented");
        }
        if (!tokens[2].equals("HTTP/1.1") && !tokens[2].equals("HTTP/1.0")) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        return new RequestLine(method, tokens[1], tokens[2]);
    }

    private List<Header> parseHeaders(final String[] lines) throws HttpProtocolException {
        if (lines.length - 1 > MAX_HEADER_COUNT) {
            throw new HttpProtocolException(431, "Request Header Fields Too Large");
        }
        final List<Header> headers = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            final String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            final int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            final String name = canonicalHeaderName(line.substring(0, separator));
            final String value = line.substring(separator + 1).trim();
            headers.add(new Header(name, value));
        }
        return headers;
    }

    private int contentLength(final List<Header> headers) throws HttpProtocolException {
        final String contentLength = header(headers, "Content-Length");
        if (contentLength == null) {
            return 0;
        }
        try {
            final int parsed = Integer.parseInt(contentLength);
            if (parsed < 0) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            return parsed;
        } catch (final NumberFormatException ex) {
            throw new HttpProtocolException(400, "Bad Request");
        }
    }

    private Query mergeQueryAndForm(final String rawQuery,
                                    final List<Header> headers,
                                    final byte[] body) {
        final List<Query.Parameter> parameters = new ArrayList<>(Query.of(rawQuery == null ? "" : rawQuery).parameters());
        final String contentType = header(headers, "Content-Type");
        if (body.length > 0
            && contentType != null
            && contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            parameters.addAll(Query.of(new String(body, StandardCharsets.UTF_8)).parameters());
        }
        return new Query(parameters);
    }

    private URI requestUri(final String target) throws HttpProtocolException {
        try {
            final URI uri = new URI(target);
            if (uri.isAbsolute()) {
                // The core request model routes by path/query even when a client sends absolute-form.
                final String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                final String rawQuery = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                return new URI(rawPath + rawQuery);
            }
            return uri;
        } catch (final URISyntaxException ex) {
            throw new HttpProtocolException(400, "Bad Request");
        }
    }

    private String absoluteUrl(final String scheme,
                               final String target,
                               final URI requestUri,
                               final List<Header> headers) {
        final URI targetUri = URI.create(target);
        if (targetUri.isAbsolute()) {
            return targetUri.toString();
        }
        final String host = header(headers, "Host");
        final String authority = host == null || host.isBlank() ? "localhost" : host;
        return scheme + "://" + authority + requestUri;
    }

    private static String header(final List<Header> headers, final String name) {
        return headers.stream()
                .filter(header -> name.equals(header.name()))
                .map(Header::value)
                .findFirst()
                .orElse(null);
    }

    private static String canonicalHeaderName(final String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "authorization" -> "Authorization";
            case "connection" -> "Connection";
            case "content-length" -> "Content-Length";
            case "content-type" -> "Content-Type";
            case "cookie" -> "Cookie";
            case "host" -> "Host";
            case "upgrade" -> "Upgrade";
            default -> name;
        };
    }

    private record RequestLine(HttpMethod method, String target, String version) {
    }
}
