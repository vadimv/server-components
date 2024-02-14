package rsp.server.http;

import java.util.Objects;

public class HttpStateOriginProvider<T> {
    private final HttpStateOriginLookup lookup;
    private final Class<T> clazz;


    public HttpStateOriginProvider(final HttpStateOriginLookup lookup,
                                   final Class<T> clazz) {
        this.lookup = Objects.requireNonNull(lookup);
        this.clazz = Objects.requireNonNull(clazz);
    }

    public T get() {
        return lookup.lookup(clazz);
    }

    public void setRelativeUrl(RelativeUrl relativeUrl) {
        lookup.setRelativeUrl(relativeUrl);
    }

    public RelativeUrl relativeUrl() {
        return lookup.relativeUrl();
    }
}
