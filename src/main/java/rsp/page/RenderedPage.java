package rsp.page;

import rsp.component.LivePageContext;
import rsp.component.StatefulComponent;
import rsp.dom.Event;

import java.util.Map;
import java.util.Objects;

public final class RenderedPage<S> {
    public final StatefulComponent<S> rootComponent;
    public final LivePageContext livePageContext;

    public RenderedPage(final StatefulComponent<S> rootComponent, final LivePageContext livePageContext) {
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.livePageContext = Objects.requireNonNull(livePageContext);
    }
}
