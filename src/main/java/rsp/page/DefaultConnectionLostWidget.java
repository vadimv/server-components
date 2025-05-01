package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.component.InitialStateComponentDefinition;
import rsp.component.StatefulComponentDefinition;
import rsp.component.View;
import rsp.dom.DefaultDomChangesContext;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
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
        final ComponentRenderContext rc = new ComponentRenderContext(qualifiedSessionId,
                                                                     PageRendering.DOCUMENT_DOM_PATH,
                                                                     pageStateOrigin,
                                                                     new SilentRemoteOut(),
                                                                     new Object());
        widgetComponent().render(rc);
        HTML = rc.html();
    }

    private static StatefulComponentDefinition<String> widgetComponent() {
        return new InitialStateComponentDefinition<>("", widget());
    }

    private static View<String> widget() {
        return __ -> div(style("position", "fixed"),
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
        public void setRenderNum(int renderNum) {
            // no-op
        }

        @Override
        public void listenEvents(List<Event> events) {
            // no-op
        }

        @Override
        public void forgetEvent(String eventType, TreePositionPath elementPath) {
            // no-op
        }

        @Override
        public void extractProperty(int descriptor, TreePositionPath path, String name) {
            // no-op
        }

        @Override
        public void modifyDom(List<DefaultDomChangesContext.DomChange> domChange) {
            // no-op
        }

        @Override
        public void setHref(String path) {
            // no-op
        }

        @Override
        public void pushHistory(String path) {
            // no-op
        }

        @Override
        public void evalJs(int descriptor, String js) {
            // no-op
        }
    }
}
