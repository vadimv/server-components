package rsp.http;

import rsp.server.http.HttpMethod;
import rsp.server.http.HttpRequest;

record ParsedHttpRequest(HttpRequest request, String version) {
    HttpMethod method() {
        return request.method;
    }
}
