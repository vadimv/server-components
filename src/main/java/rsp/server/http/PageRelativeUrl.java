package rsp.server.http;

import java.util.Objects;

public class PageRelativeUrl {

    private volatile RelativeUrl relativeUrl;

    public PageRelativeUrl(final RelativeUrl relativeUrl) {
        this.relativeUrl = Objects.requireNonNull(relativeUrl);
    }

    public void set(final RelativeUrl relativeUrl) {
        this.relativeUrl = relativeUrl;
    }

    public RelativeUrl get() {
        return relativeUrl;
    }
}
