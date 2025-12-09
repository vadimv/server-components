package rsp.server.http;

import rsp.server.Path;

import java.util.Objects;

/**
 * Represents a URL part starting from path component
 * @param path
 * @param query
 * @param fragment
 */
public record RelativeUrl(Path path, Query query, Fragment fragment) {
    public RelativeUrl {
        Objects.requireNonNull(path);
        Objects.requireNonNull(query);
        Objects.requireNonNull(fragment);
    }

    public static RelativeUrl of(HttpRequest httpRequest) {
        return new RelativeUrl(httpRequest.path, httpRequest.queryParameters, new Fragment(httpRequest.uri.getFragment()));
    }

    @Override
    public String toString() {
        return path.toString() + query.toString() + fragment.toString();
    }
}
