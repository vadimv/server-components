package rsp.page;

import rsp.component.ComponentDsl;
import rsp.component.ComponentRenderContext;
import rsp.dom.DefaultDomChangesContext;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.html.SegmentDefinition;
import rsp.html.TagDefinition;
import rsp.server.Path;
import rsp.server.RemoteOut;
import rsp.server.http.HttpRequest;
import rsp.server.http.PageStateOrigin;

import java.net.URI;
import java.util.List;

import static rsp.html.HtmlDsl.*;

public final class DefaultConnectionLostWidget {

    private DefaultConnectionLostWidget() {}

    public static final String HTML;

    static {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("0", "0");
        final URI uri = URI.create("http://localhost");
        final HttpRequest httpRequest = new HttpRequest(HttpRequest.HttpMethod.GET,
                                                        uri,
                                                        uri.toString(),
                                                        Path.ROOT);
        final PageStateOrigin pageStateOrigin = new PageStateOrigin(httpRequest);
        final RemoteOut remoteOut = new SilentRemoteOut();
        final ComponentRenderContext rc = new ComponentRenderContext(qualifiedSessionId,
                                                                     VirtualDomPath.of("1"),
                                                                     pageStateOrigin,
                                                                     remoteOut,
                                                                     new Object());
        widgetComponent().render(rc);
        HTML = rc.toString();
    }

    private static SegmentDefinition widgetComponent() {
        return ComponentDsl.component("", __ -> ___ -> widget());
    }

    private static TagDefinition widget() {
        return div(style("position", "fixed"),
                   style("top", "0"),
                   style("left", "0"),
                   style("right", "0"),
                   style("background-color", "lightyellow"),
                   style("border-bottom", "1px solid black"),
                   style("padding", "10px"),
                   text("Connection lost. Waiting to resume."));
    }

    private static class SilentRemoteOut implements RemoteOut {

        @Override
        public void setRenderNum(int renderNum) {}

        @Override
        public void listenEvents(List<Event> events) {}

        @Override
        public void forgetEvent(String eventType, VirtualDomPath elementPath) {}

        @Override
        public void extractProperty(int descriptor, VirtualDomPath path, String name) {}

        @Override
        public void modifyDom(List<DefaultDomChangesContext.DomChange> domChange) {}

        @Override
        public void setHref(String path) {}

        @Override
        public void pushHistory(String path) {}

        @Override
        public void evalJs(int descriptor, String js) {}
    }
}
