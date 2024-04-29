package rsp.page;

import rsp.component.Component;
import rsp.server.http.PageStateOrigin;

import java.util.Objects;

public final class RenderedPage<S> {
    public final Component<S> rootComponent;
    public final TemporaryBufferedPageCommands commandsBuffer;
    public final Object sessionLock;

    public RenderedPage(final Component<S> rootComponent,
                        final TemporaryBufferedPageCommands commandsBuffer,
                        final Object sessionLock) {

        this.rootComponent = Objects.requireNonNull(rootComponent);
        this.commandsBuffer = Objects.requireNonNull(commandsBuffer);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }
}
