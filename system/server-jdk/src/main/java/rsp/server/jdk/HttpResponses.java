package rsp.server.jdk;

import rsp.http.HttpResponse;
import rsp.http.HttpStatus;

final class HttpResponses {
    private HttpResponses() {
    }

    static HttpResponse status(int status) {
        HttpStatus value = HttpStatus.of(status);
        String text = value.reasonPhrase().isEmpty()
                ? Integer.toString(status)
                : status + " " + value.reasonPhrase();
        return HttpResponse.status(value).text(text).build();
    }
}
