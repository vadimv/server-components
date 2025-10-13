package rsp.page;

import java.util.Objects;

public final class RenderedPage {  // TODO
    public final PageRenderContext pageRenderContext;
    public final RedirectableEventsConsumer commandsBuffer;

    public RenderedPage(final PageRenderContext pageRenderContext,
                        final RedirectableEventsConsumer commandsBuffer) {

        this.pageRenderContext = Objects.requireNonNull(pageRenderContext);
        this.commandsBuffer = Objects.requireNonNull(commandsBuffer);
    }
}
