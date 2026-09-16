package rsp.http;

record ParsedHttpRequest(HttpRequest request, String version) {
    HttpMethod method() {
        return request.method();
    }
}
