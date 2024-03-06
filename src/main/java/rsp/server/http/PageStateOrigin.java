package rsp.server.http;

import java.util.Objects;

public class PageStateOrigin {

    private final HttpRequest httpRequest;

    private volatile RelativeUrl relativeUrl;

    public PageStateOrigin(final HttpRequest httpRequest) {
        this.httpRequest = Objects.requireNonNull(httpRequest);
        relativeUrl = RelativeUrl.of(httpRequest);
    }

    public void setRelativeUrl(final RelativeUrl relativeUrl) {
        this.relativeUrl = relativeUrl;
    }

    public RelativeUrl getRelativeUrl() {
        return relativeUrl;
    }

    public HttpStateOrigin httpStateOrigin() {
        return new HttpStateOrigin(httpRequest, relativeUrl);
    }
}
