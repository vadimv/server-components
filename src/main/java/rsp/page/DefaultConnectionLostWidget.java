package rsp.page;

import rsp.component.ComponentContext;
import rsp.component.TreeBuilder;
import rsp.component.definitions.InitialStateComponent;
import rsp.component.definitions.Component;
import rsp.component.View;
import rsp.dom.DefaultDomChangesContext;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.server.RemoteOut;

import java.util.List;

import static rsp.dsl.Html.*;
import static rsp.page.PageBuilder.DOCUMENT_DOM_PATH;

public final class DefaultConnectionLostWidget {

    private DefaultConnectionLostWidget() {}

    public static final String HTML;

    static {
        final QualifiedSessionId qualifiedSessionId = new QualifiedSessionId("0", "0");
        final TreeBuilder treeBuilder = new TreeBuilder(qualifiedSessionId,
                                                        DOCUMENT_DOM_PATH,
                                                        new ComponentContext(),
                                                       _ -> new SilentRemoteOut());
        widgetComponent().render(treeBuilder);
        HTML = treeBuilder.html();
    }

    private static Component<String> widgetComponent() {
        return new InitialStateComponent<>("", widget());
    }

    private static View<String> widget() {
        return _ -> div(style("position", "fixed"),
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
