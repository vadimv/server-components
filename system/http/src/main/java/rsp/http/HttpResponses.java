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

    static HttpResponse status(final int status) {
        final String reasonPhrase = reasonPhrase(status);
        return text(status, reasonPhrase.isEmpty() ? Integer.toString(status) : status + " " + reasonPhrase);
    }

    static String reasonPhrase(final int status) {
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
