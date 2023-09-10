package rsp.page;

import rsp.component.Component;
import rsp.server.RemoteOut;
import rsp.server.http.HttpStateOriginLookup;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RenderedPage<S> {
    public final HttpStateOriginLookup httpStateOriginLookup;
    public final Component<?, S> rootComponent;
    public final AtomicReference<RemoteOut> remoteOutReference;

    public RenderedPage(final HttpStateOriginLookup httpStateOriginLookup,
                        final Component<?, S> rootComponent,
                        final AtomicReference<RemoteOut> remoteOutReference) {

        this.httpStateOriginLookup = Objects.requireNonNull(httpStateOriginLookup);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.remoteOutReference = Objects.requireNonNull(remoteOutReference);
    }
}
