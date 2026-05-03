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
        return _ -> div(attr("class", "rsp-connection-lost"),
                   attr("style", "position: fixed;"
                                                + "top: 12px;"
                                                + "left: 50%;"
                                                + "transform: translateX(-50%);"
                                                + "z-index: 2147483647;"
                                                + "max-width: calc(100vw - 32px);"
                                                + "box-sizing: border-box;"
                                                + "background-color: #18191b;"
                                                + "color: #f5f5f6;"
                                                + "border: 1px solid #4b4e54;"
                                                + "border-left: 3px solid #d97706;"
                                                + "border-radius: 8px;"
                                                + "box-shadow: 0 12px 30px rgba(0, 0, 0, 0.35);"
                                                + "padding: 10px 14px;"
                                                + "font: 500 14px/1.4 system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif;"),
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
