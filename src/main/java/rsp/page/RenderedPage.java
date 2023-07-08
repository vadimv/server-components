package rsp.page;

import rsp.component.Component;
import rsp.server.http.HttpRequestLookup;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RenderedPage<S> {
    public final HttpRequestLookup httpRequestLookup;
    public final Component<?, S> rootComponent;
    public final AtomicReference<LivePage> livePageContext;

    public RenderedPage(final HttpRequestLookup httpRequestLookup,
                        final Component<?, S> rootComponent,
                        final AtomicReference<LivePage> livePageContext) {

        this.httpRequestLookup = Objects.requireNonNull(httpRequestLookup);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
