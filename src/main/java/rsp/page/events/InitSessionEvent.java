package rsp.page.events;

import rsp.page.PageRenderContext;
import rsp.page.RedirectableEventsConsumer;
import rsp.server.RemoteOut;

public record InitSessionEvent(PageRenderContext pageRenderContext,
                               RedirectableEventsConsumer commandsEnqueue,
                               RemoteOut remoteOut) implements SessionEvent {
}
