package rsp.page.events;

import rsp.page.PageBuilder;
import rsp.page.PageScope;
import rsp.page.RedirectableEventsConsumer;
import rsp.server.RemoteOut;

public record InitSessionCommand(PageBuilder pageRenderContext,
                                 RedirectableEventsConsumer commandsEnqueue,
                                 RemoteOut remoteOut,
                                 PageScope scope) implements Command {
}
