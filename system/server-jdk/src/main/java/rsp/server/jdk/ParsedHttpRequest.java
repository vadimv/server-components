package rsp.server.jdk;

import rsp.http.HttpMethod;
import rsp.http.HttpRequest;

record ParsedHttpRequest(HttpRequest request, String version) {
    HttpMethod method() {
        return request.method();
    }
}
