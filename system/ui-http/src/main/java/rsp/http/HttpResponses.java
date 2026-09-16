package rsp.http;

final class HttpResponses {
    private HttpResponses() {
    }

    static HttpResponse text(final int status, final String body) {
        return HttpResponse.status(HttpStatus.of(status)).text(body).build();
    }

    static HttpResponse status(final int status) {
        final HttpStatus value = HttpStatus.of(status);
        return text(status, value.reasonPhrase().isEmpty()
                ? Integer.toString(status)
                : status + " " + value.reasonPhrase());
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
