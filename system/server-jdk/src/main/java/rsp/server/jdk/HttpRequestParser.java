package rsp.server.jdk;

import rsp.http.HttpHeader;
import rsp.http.HttpHeaders;
import rsp.http.HttpMethod;
import rsp.http.HttpRequest;
import rsp.http.RequestBody;
import rsp.url.Path;
import rsp.url.Query;

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
    static final int MAX_REQUEST_BODY_BYTES = 256 * 1024;
    private static final int BODY_READ_TIMEOUT_MS = 10_000;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_REQUEST_LINE_BYTES = 8 * 1024;
    private static final int MAX_HEADER_COUNT = 100;

    Optional<ParsedHttpRequest> parse(Socket socket, String scheme) throws IOException, HttpProtocolException {
        InputStream input = socket.getInputStream();
        byte[] headerBytes = readHeaderBytes(input);
        if (headerBytes.length == 0) {
            return Optional.empty();
        }
        String headerBlock = new String(headerBytes, StandardCharsets.ISO_8859_1);
        String[] lines = headerBlock.substring(0, headerBlock.length() - 4).split("\r\n", -1);
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        if (lines[0].getBytes(StandardCharsets.ISO_8859_1).length > MAX_REQUEST_LINE_BYTES) {
            throw new HttpProtocolException(414, "URI Too Long");
        }

        RequestLine requestLine = parseRequestLine(lines[0]);
        List<HttpHeader> headers = parseHeaders(lines);
        int contentLength = contentLength(headers);
        byte[] body = new byte[0];
        if (contentLength > 0) {
            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                throw new HttpProtocolException(413, "Payload Too Large");
            }
            socket.setSoTimeout(BODY_READ_TIMEOUT_MS);
            body = input.readNBytes(contentLength);
            if (body.length != contentLength) {
                throw new HttpProtocolException(400, "Bad Request");
            }
        }

        URI requestUri = requestUri(requestLine.target());
        String rawQuery = requestUri.getRawQuery();
        Query query = mergeQueryAndForm(rawQuery, headers, body);
        String rawPath = requestUri.getRawPath() == null || requestUri.getRawPath().isEmpty()
                ? "/" : requestUri.getRawPath();
        String url = absoluteUrl(scheme, requestLine.target(), requestUri, headers);
        HttpRequest request = new HttpRequest(requestLine.method(), requestLine.target(), rawPath, requestUri, url,
                Path.parse(rawPath), query, HttpHeaders.copyOf(headers),
                RequestBody.of(body, MAX_REQUEST_BODY_BYTES));
        return Optional.of(new ParsedHttpRequest(request, requestLine.version()));
    }

    private byte[] readHeaderBytes(InputStream input) throws IOException, HttpProtocolException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b1 = -1;
        int b2 = -1;
        int b3 = -1;
        int b4 = -1;
        while (buffer.size() <= MAX_HEADER_BYTES) {
            int next;
            try {
                next = input.read();
            } catch (SocketTimeoutException failure) {
                throw new HttpProtocolException(408, "Request Timeout");
            }
            if (next < 0) {
                if (buffer.size() == 0) {
                    return new byte[0];
                }
                throw new HttpProtocolException(400, "Bad Request");
            }
            buffer.write(next);
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

    private RequestLine parseRequestLine(String line) throws HttpProtocolException {
        String[] tokens = line.split(" +", 3);
        if (tokens.length != 3) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(tokens[0]);
        } catch (IllegalArgumentException failure) {
            throw new HttpProtocolException(501, "Not Implemented");
        }
        if (!tokens[2].equals("HTTP/1.1") && !tokens[2].equals("HTTP/1.0")) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        return new RequestLine(method, tokens[1], tokens[2]);
    }

    private List<HttpHeader> parseHeaders(String[] lines) throws HttpProtocolException {
        if (lines.length - 1 > MAX_HEADER_COUNT) {
            throw new HttpProtocolException(431, "Request Header Fields Too Large");
        }
        List<HttpHeader> headers = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            try {
                headers.add(new HttpHeader(canonicalHeaderName(line.substring(0, separator)),
                        line.substring(separator + 1).trim()));
            } catch (IllegalArgumentException failure) {
                throw new HttpProtocolException(400, "Bad Request");
            }
        }
        return headers;
    }

    private int contentLength(List<HttpHeader> headers) throws HttpProtocolException {
        List<String> values = headers.stream()
                .filter(header -> "Content-Length".equalsIgnoreCase(header.name()))
                .map(HttpHeader::value)
                .toList();
        if (values.isEmpty()) {
            return 0;
        }
        if (values.stream().distinct().count() != 1) {
            throw new HttpProtocolException(400, "Bad Request");
        }
        try {
            int parsed = Integer.parseInt(values.getFirst());
            if (parsed < 0) {
                throw new HttpProtocolException(400, "Bad Request");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new HttpProtocolException(400, "Bad Request");
        }
    }

    private Query mergeQueryAndForm(String rawQuery, List<HttpHeader> headers, byte[] body) {
        List<Query.Parameter> parameters = new ArrayList<>(Query.of(rawQuery == null ? "" : rawQuery).parameters());
        String contentType = header(headers, "Content-Type");
        if (body.length > 0 && contentType != null
                && contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            parameters.addAll(Query.of(new String(body, StandardCharsets.UTF_8)).parameters());
        }
        return new Query(parameters);
    }

    private URI requestUri(String target) throws HttpProtocolException {
        try {
            URI uri = new URI(target);
            if (uri.isAbsolute()) {
                String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                String rawQuery = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                return new URI(rawPath + rawQuery);
            }
            return uri;
        } catch (URISyntaxException failure) {
            throw new HttpProtocolException(400, "Bad Request");
        }
    }

    private String absoluteUrl(String scheme, String target, URI requestUri, List<HttpHeader> headers) {
        URI targetUri = URI.create(target);
        if (targetUri.isAbsolute()) {
            return targetUri.toString();
        }
        String host = header(headers, "Host");
        String authority = host == null || host.isBlank() ? "localhost" : host;
        return scheme + "://" + authority + requestUri;
    }

    private static String header(List<HttpHeader> headers, String name) {
        return headers.stream().filter(header -> name.equalsIgnoreCase(header.name()))
                .map(HttpHeader::value).findFirst().orElse(null);
    }

    private static String canonicalHeaderName(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "authorization" -> "Authorization";
            case "connection" -> "Connection";
            case "content-length" -> "Content-Length";
            case "content-type" -> "Content-Type";
            case "cookie" -> "Cookie";
            case "host" -> "Host";
            case "origin" -> "Origin";
            case "sec-websocket-extensions" -> "Sec-WebSocket-Extensions";
            case "sec-websocket-key" -> "Sec-WebSocket-Key";
            case "sec-websocket-protocol" -> "Sec-WebSocket-Protocol";
            case "sec-websocket-version" -> "Sec-WebSocket-Version";
            case "upgrade" -> "Upgrade";
            default -> name;
        };
    }

    private record RequestLine(HttpMethod method, String target, String version) {
    }
}
