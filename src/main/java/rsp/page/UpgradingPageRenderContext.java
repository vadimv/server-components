package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public final class UpgradingPageRenderContext implements PageRenderContext {

    private final PageRenderContext context;
    private final String pageInfo;

    private boolean headWasOpened = false;

    private UpgradingPageRenderContext(PageRenderContext context, String pageInfo) {
        this.context = context;
        this.pageInfo = pageInfo;
    }

    public static UpgradingPageRenderContext create(PageRenderContext context,
                                                    String sessionId,
                                                    String path,
                                                    String connectionLostWidgetHtml,
                                                    int heartBeatInterval) {
        final String cfg = "window['kfg']={"
                + "sid:'" + sessionId + "',"
                + "r:'" + path + "',"
                + "clw:'" + connectionLostWidgetHtml + "',"
                + "heartbeatInterval:" + heartBeatInterval
                + "}";
        return new UpgradingPageRenderContext(context, cfg);
    }

    @Override
    public void setStatusCode(int statusCode) {
        context.setStatusCode(statusCode);
    }

    @Override
    public void setHeaders(Map<String, String> headers) {
        context.setHeaders(headers);
    }

    @Override
    public void setDocType(String docType) {
        context.setDocType(docType);
    }

    @Override
    public void openNode(XmlNs xmlNs, String name) {
        if (!headWasOpened && xmlNs.equals(XmlNs.html) && name.equals("body")) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            context.openNode(XmlNs.html, "head");
            upgradeHeadTag();
            context.closeNode("head", false);
        } else if (xmlNs.equals(XmlNs.html) && name.equals("head")) {
            headWasOpened = true;
        }
        context.openNode(xmlNs, name);
    }

    @Override
    public void closeNode(String name, boolean upgrade) {
        if (headWasOpened && upgrade && name.equals("head")) {
            upgradeHeadTag();
        }
        context.closeNode(name, upgrade);
    }

    private void upgradeHeadTag() {
        context.openNode(XmlNs.html, "script");
        context.addTextNode(pageInfo);
        context.closeNode("script", false);

        context.openNode(XmlNs.html, "script");
        context.setAttr(XmlNs.html, "src", "/static/rsp-client.min.js", false);
        context.setAttr(XmlNs.html, "defer", "defer", true);
        context.closeNode("script", true);
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty) {
        context.setAttr(xmlNs, name, value, isProperty);
    }

    @Override
    public void setStyle(String name, String value) {
        context.setStyle(name, value);
    }

    @Override
    public void addTextNode(String text) {
        context.addTextNode(text);
    }

    @Override
    public <S> void addEvent(Optional<VirtualDomPath> elementPath,
                             String eventName,
                             Consumer<EventContext<S>> eventHandler,
                             boolean preventDefault,
                             Event.Modifier modifier) {
       context.addEvent(elementPath, eventName, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addRef(Ref ref) {
        context.addRef(ref);
    }

    @Override
    public void openComponent() {
        context.openComponent();
    }

    @Override
    public void closeComponent() {
        context.closeComponent();
    }

    @Override
    public <S2, S1> void openComponent(Function<Consumer<S2>, Consumer<S1>> componentSetState) {
        context.openComponent(componentSetState);
    }

    @Override
    public String toString() {
        return context.toString();
    }
}
