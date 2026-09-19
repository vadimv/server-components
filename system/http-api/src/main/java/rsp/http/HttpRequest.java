package rsp.http;

import rsp.url.Fragment;
import rsp.url.Path;
import rsp.url.Query;
import rsp.url.RelativeUrl;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable HTTP request data, independent of a server implementation. */
public record HttpRequest(HttpMethod method,
                          String rawTarget,
                          String rawPath,
                          URI uri,
                          String absoluteUrl,
                          Path path,
                          Query query,
                          HttpHeaders headers,
                          RequestBody body) {
    public HttpRequest {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rawTarget, "rawTarget");
        Objects.requireNonNull(rawPath, "rawPath");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(absoluteUrl, "absoluteUrl");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
    }

    public String header(String name) {
        return headers.first(name).orElse(null);
    }

    /** Returns a copy with all values for {@code name} replaced by one value. */
    public HttpRequest withHeader(String name, String value) {
        HttpHeaders replaced = HttpHeaders.builder()
                .addAll(headers)
                .set(name, value)
                .build();
        return new HttpRequest(method, rawTarget, rawPath, uri, absoluteUrl, path, query,
                replaced, body);
    }

    public List<String> cookies(String name) {
        Objects.requireNonNull(name, "name");
        return headers.all("Cookie").stream()
                .flatMap(value -> Arrays.stream(value.split(";")))
                .map(String::trim)
                .map(value -> value.split("=", 2))
                .filter(pair -> pair.length == 2 && pair[0].equals(name))
                .map(pair -> pair[1])
                .toList();
    }

    public RelativeUrl relativeUrl() {
        return new RelativeUrl(path, query, Fragment.EMPTY);
    }
}
