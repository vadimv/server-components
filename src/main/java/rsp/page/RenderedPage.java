package rsp.page;

import java.util.Objects;

public record RenderedPage(PageBuilder pageRenderContext, RedirectableEventsConsumer commandsEnqueue) {
    public RenderedPage(final PageBuilder pageRenderContext,
                        final RedirectableEventsConsumer commandsEnqueue) {

        this.pageRenderContext = Objects.requireNonNull(pageRenderContext);
        this.commandsEnqueue = Objects.requireNonNull(commandsEnqueue);
    }
}
