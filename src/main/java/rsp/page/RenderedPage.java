package rsp.page;

import rsp.component.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RenderedPage<S> {
    public final Component<S> rootComponent;
    public final AtomicReference<LivePage> livePageContext;

    public RenderedPage(final Component<S> rootComponent, final AtomicReference<LivePage> livePageContext) {
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
