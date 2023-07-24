package rsp.page;

import rsp.component.Component;
import rsp.server.http.StateOriginLookup;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RenderedPage<S> {
    public final StateOriginLookup stateOriginLookup;
    public final Component<?, S> rootComponent;
    public final AtomicReference<LivePage> livePageContext;

    public RenderedPage(final StateOriginLookup stateOriginLookup,
                        final Component<?, S> rootComponent,
                        final AtomicReference<LivePage> livePageContext) {

        this.stateOriginLookup = Objects.requireNonNull(stateOriginLookup);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
