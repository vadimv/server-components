package rsp.page.events;

import rsp.page.PageRenderContext;
import rsp.server.RemoteOut;

public record InitSessionEvent(PageRenderContext pageRenderContext, RemoteOut remoteOut) implements SessionEvent {
}
