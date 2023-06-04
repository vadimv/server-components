package rsp.page;

import rsp.component.OutContext;
import rsp.component.StatefulComponent;

import java.util.Objects;

public final class RenderedPageSnapshot<S> {
    public final StatefulComponent<S> rootComponent;
    public final OutContext outContext;

    public RenderedPageSnapshot(final StatefulComponent<S> rootComponent, final OutContext outContext) {
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.outContext = Objects.requireNonNull(outContext);
    }
}
