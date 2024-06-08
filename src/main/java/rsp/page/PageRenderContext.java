package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.dom.TreePositionPath;
import rsp.dom.XmlNs;
import rsp.server.RemoteOut;
import rsp.server.http.PageStateOrigin;

import java.util.Map;
import java.util.Objects;

public final class PageRenderContext extends ComponentRenderContext {

    private final String pageConfigScript;

    private int statusCode;
    private Map<String, String> headers;
    private boolean headWasOpened;

    public PageRenderContext(final QualifiedSessionId sessionId,
                             final String pageConfigScript,
                             final TreePositionPath rootDomPath,
                             final PageStateOrigin httpStateOriginSupplier,
                             final RemoteOut remotePageMessagesOut,
                             final Object sessionLock) {
        super(sessionId,
              rootDomPath,
              httpStateOriginSupplier,
              remotePageMessagesOut,
              sessionLock);
        this.pageConfigScript = Objects.requireNonNull(pageConfigScript);
    }

    public int statusCode() {
        return statusCode;
    }

    public void setStatusCode(final int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void openNode(final String name, boolean isSelfClosing) {
        if (!headWasOpened && name.equals("body")) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            super.openNode("head", false);
            upgradeHeadTag();
            super.closeNode("head", false);
        } else if (name.equals("head")) {
            headWasOpened = true;
        }
        super.openNode(name, isSelfClosing);
    }

    @Override
    public void closeNode(final String name, final boolean upgrade) {
        if (headWasOpened && upgrade && name.equals("head")) {
            upgradeHeadTag();
        }
        super.closeNode(name, upgrade);
    }

    private void upgradeHeadTag() {
        super.openNode( "script", false);
        super.addTextNode(pageConfigScript);
        super.closeNode("script", false);

        super.openNode("script", false);
        super.setAttr("src", "/static/rsp-client.min.js", false);
        super.setAttr("defer", "defer", true);
        super.closeNode("script", true);
    }

    @Override
    public ComponentRenderContext newContext(final TreePositionPath startDomPath) {
        return startDomPath.equals(PageRendering.DOCUMENT_DOM_PATH) ? new PageRenderContext(sessionId,
                                                                                            pageConfigScript,
                                                                                            startDomPath,
                                                                                            pageStateOrigin,
                                                                                            remotePageMessagesOut,
                                                                                            sessionLock)
                                                             : super.newContext(startDomPath);
    }
}
