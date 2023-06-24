package rsp.page;

import rsp.component.Component;
import rsp.component.ComponentDefinition;
import rsp.server.HttpRequest;
import rsp.server.Path;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RenderedPage<S> {
    public final HttpRequest httpRequest;
    public final Component<S> rootComponent;
    public final AtomicReference<LivePage> livePageContext;

    public RenderedPage(final HttpRequest httpRequest,
                        final Component<S> rootComponent,
                        final AtomicReference<LivePage> livePageContext) {

        this.httpRequest = Objects.requireNonNull(httpRequest);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
