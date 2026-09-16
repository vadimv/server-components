package rsp.http;

import java.util.Objects;

/** A validated HTTP status code and reason phrase. */
public record HttpStatus(int code, String reasonPhrase) {
    public static final HttpStatus OK = new HttpStatus(200, "OK");
    public static final HttpStatus FOUND = new HttpStatus(302, "Found");
    public static final HttpStatus BAD_REQUEST = new HttpStatus(400, "Bad Request");
    public static final HttpStatus UNAUTHORIZED = new HttpStatus(401, "Unauthorized");
    public static final HttpStatus FORBIDDEN = new HttpStatus(403, "Forbidden");
    public static final HttpStatus NOT_FOUND = new HttpStatus(404, "Not Found");
    public static final HttpStatus METHOD_NOT_ALLOWED = new HttpStatus(405, "Method Not Allowed");
    public static final HttpStatus REQUEST_TIMEOUT = new HttpStatus(408, "Request Timeout");
    public static final HttpStatus PAYLOAD_TOO_LARGE = new HttpStatus(413, "Payload Too Large");
    public static final HttpStatus URI_TOO_LONG = new HttpStatus(414, "URI Too Long");
    public static final HttpStatus REQUEST_HEADER_FIELDS_TOO_LARGE =
            new HttpStatus(431, "Request Header Fields Too Large");
    public static final HttpStatus INTERNAL_SERVER_ERROR = new HttpStatus(500, "Internal Server Error");
    public static final HttpStatus NOT_IMPLEMENTED = new HttpStatus(501, "Not Implemented");

    public HttpStatus {
        if (code < 100 || code > 999) {
            throw new IllegalArgumentException("HTTP status must be between 100 and 999: " + code);
        }
        reasonPhrase = Objects.requireNonNull(reasonPhrase, "reasonPhrase");
        if (reasonPhrase.indexOf('\r') >= 0 || reasonPhrase.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("HTTP reason phrase cannot contain a line break");
        }
    }

    public static HttpStatus of(int code) {
        return switch (code) {
            case 200 -> OK;
            case 302 -> FOUND;
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 408 -> REQUEST_TIMEOUT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 414 -> URI_TOO_LONG;
            case 431 -> REQUEST_HEADER_FIELDS_TOO_LARGE;
            case 500 -> INTERNAL_SERVER_ERROR;
            case 501 -> NOT_IMPLEMENTED;
            default -> new HttpStatus(code, "");
        };
    }
}
