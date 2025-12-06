package rsp.page;

import rsp.component.ComponentContext;
import rsp.component.ComponentRenderContext;
import rsp.component.definitions.InitialStateComponent;
import rsp.component.definitions.StatefulComponent;
import rsp.component.View;
import rsp.dom.DefaultDomChangesContext;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.server.RemoteOut;

import java.util.List;

import static rsp.dsl.Html.*;

public final class DefaultConnectionLostWidget {

    private DefaultConnectionLostWidget() {}

    public static final String HTML;

    static {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("0", "0");
        final ComponentRenderContext rc = new ComponentRenderContext(qualifiedSessionId,
                                                                     PageRendering.DOCUMENT_DOM_PATH,
                                                                     new ComponentContext(),
                                                                     __ -> new SilentRemoteOut());
        widgetComponent().render(rc);
        HTML = rc.html();
    }

    private static StatefulComponent<String> widgetComponent() {
        return new InitialStateComponent<>("", widget());
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
        public void listenEvents(List<DomEventEntry> events) {
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
