package rsp.url;

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

    @Override
    public String toString() {
        return path.toString() + query.toString() + fragment.toString();
    }
}
