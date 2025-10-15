package rsp.page;

import java.util.Objects;

public final class RenderedPage {  // TODO
    public final PageRenderContext pageRenderContext;
    public final RedirectableEventsConsumer commandsEnqueue;

    public RenderedPage(final PageRenderContext pageRenderContext,
                        final RedirectableEventsConsumer commandsEnqueue) {

        this.pageRenderContext = Objects.requireNonNull(pageRenderContext);
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);
    }
}
