package rsp.page;

import java.util.Objects;

public final class RenderedPage {  // TODO
    public final PageRenderContext pageRenderContext;
    public final TemporaryBufferedPageCommands commandsBuffer;
    public final Object sessionLock;

    public RenderedPage(final PageRenderContext pageRenderContext,
                        final TemporaryBufferedPageCommands commandsBuffer,
                        final Object sessionLock) {

        this.pageRenderContext = Objects.requireNonNull(pageRenderContext);
        this.commandsBuffer = Objects.requireNonNull(commandsBuffer);
        this.sessionLock = Objects.requireNonNull(sessionLock);
    }
}
