package rsp.page;

import rsp.component.Component;
import rsp.server.http.PageStateOrigin;

import java.util.Objects;

public final class RenderedPage<S> {
    public final PageStateOrigin pageStateOrigin;
    public final Component<S> rootComponent;
    public final TemporaryBufferedPageCommands commandsBuffer;

    public RenderedPage(final PageStateOrigin pageStateOrigin,
                        final Component<S> rootComponent,
                        final TemporaryBufferedPageCommands commandsBuffer) {

        this.pageStateOrigin = Objects.requireNonNull(pageStateOrigin);
        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.commandsBuffer = Objects.requireNonNull(commandsBuffer);
    }
}
