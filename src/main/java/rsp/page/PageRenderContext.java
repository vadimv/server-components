package rsp.page;

import rsp.component.ComponentContext;
import rsp.component.ComponentRenderContext;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.page.events.SessionEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class PageRenderContext extends ComponentRenderContext {

    private final String pageConfigScript;

    private int statusCode;
    private Map<String, List<String>> headers;
    private boolean headWasOpened;

    public PageRenderContext(final QualifiedSessionId sessionId,
                             final String pageConfigScript,
                             final TreePositionPath rootDomPath,
                             final ComponentContext componentContext,
                             final Consumer<SessionEvent> remotePageMessagesOut) {
        super(sessionId,
              rootDomPath,
              componentContext,
              remotePageMessagesOut);
        this.pageConfigScript = Objects.requireNonNull(pageConfigScript);
    }

    public int statusCode() {
        return statusCode;
    }

    public void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public void setHeaders(final Map<String, List<String>> headers) {
        this.headers = headers;
    }

    @Override
    public void openNode(final XmlNs xmlNs, final String name, boolean isSelfClosing) {
        if (!headWasOpened && xmlNs.equals(XmlNs.html) && name.equals("body")) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            super.openNode(XmlNs.html, "head", false);
            upgradeHeadTag();
            super.closeNode("head", false);
        } else if (xmlNs.equals(XmlNs.html) && name.equals("head")) {
            headWasOpened = true;
        }
        super.openNode(xmlNs, name, isSelfClosing);
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        if (headWasOpened && upgrade && name.equals("head")) {
            upgradeHeadTag();
        }
        super.closeNode(name, upgrade);
    }

    private void upgradeHeadTag() {
        super.openNode(XmlNs.html, "script", false);
        super.addTextNode(pageConfigScript);
        super.closeNode("script", false);

        super.openNode(XmlNs.html, "script", false);
        super.setAttr(XmlNs.html, "src", "/static/rsp-client.min.js", false);
        super.setAttr(XmlNs.html, "defer", "defer", true);
        super.closeNode("script", true);
    }

    @Override
    public ComponentRenderContext newContext(final TreePositionPath startDomPath) {
        return PageRendering.DOCUMENT_DOM_PATH.equals(startDomPath) ? new PageRenderContext(sessionId,
                                                                                            pageConfigScript,
                                                                                            startDomPath,
                                                                                            componentContext,
                                                                                            remotePageMessagesOut)
                                                             : super.newContext(startDomPath);
    }
}
