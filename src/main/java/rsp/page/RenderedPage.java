package rsp.page;

import rsp.component.LivePageContext;
import rsp.component.Component;

import java.util.Objects;

public final class RenderedPage<S> {
    public final Component<S> rootComponent;
    public final LivePageContext livePageContext;

    public RenderedPage(final Component<S> rootComponent, final LivePageContext livePageContext) {
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
