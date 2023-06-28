package rsp.server;

import rsp.util.Lookup;

public class HttpRequestLookup implements Lookup {

    private final HttpRequest httpRequest;

    public HttpRequestLookup(HttpRequest httpRequest) {
        this.httpRequest = httpRequest;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T lookup(final Class<T> clazz) {
        if (HttpRequest.class.equals(clazz)) {
            return (T) httpRequest;
        } else if (Path.class.equals(clazz)) {
            return (T) httpRequest.path;
        } else {
            throw new IllegalStateException("Lookup failed for an unsupported state reference type: " + clazz);
        }
    }
}
