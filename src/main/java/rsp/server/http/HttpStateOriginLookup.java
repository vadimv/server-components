package rsp.server.http;

import rsp.server.Path;
import rsp.util.Lookup;

import java.util.Objects;

public class HttpStateOriginLookup implements Lookup {

    private volatile HttpStateOrigin stateOrigin;

    public HttpStateOriginLookup(final HttpStateOrigin stateOrigin) {
        this.stateOrigin = Objects.requireNonNull(stateOrigin);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T lookup(final Class<T> clazz) {
        if (HttpRequest.class.equals(clazz)) {
            return (T) stateOrigin.httpRequest();
        } else if (Path.class.equals(clazz)) {
            return (T) stateOrigin.relativeUrl().path();
        } else {
            throw new IllegalStateException("Lookup failed for an unsupported state reference type: " + clazz);
        }
    }

    public void setRelativeUrl(RelativeUrl relativeUrl) {
        stateOrigin = new HttpStateOrigin(stateOrigin.httpRequest(), relativeUrl);
    }

    public RelativeUrl relativeUrl() {
        return stateOrigin.relativeUrl();
    }
}
