package rsp.server.http;

import rsp.server.Path;

import java.util.Objects;

public record RelativeUrl(Path path, Query query, Fragment fragment) {
    public RelativeUrl {
        Objects.requireNonNull(path);
        Objects.requireNonNull(query);
        Objects.requireNonNull(fragment);
    }

    public static RelativeUrl of(HttpRequest httpRequest) {
        return new RelativeUrl(httpRequest.path, Query.of(""), Fragment.of(""));
    }
}
