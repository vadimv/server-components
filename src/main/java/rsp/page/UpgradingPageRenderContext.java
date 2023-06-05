package rsp.page;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class UpgradingPageRenderContext implements PageRenderContext {

    private final PageRenderContext context;
    private final String pageInfo;

    private boolean headWasOpened = false;

    private UpgradingPageRenderContext(final PageRenderContext context, final String pageInfo) {
        this.context = context;
        this.pageInfo = pageInfo;
    }

    public static UpgradingPageRenderContext create(final PageRenderContext context,
                                                    final String sessionId,
                                                    final String path,
                                                    final String connectionLostWidgetHtml,
                                                    final int heartBeatInterval) {
        final String cfg = "window['kfg']={"
                + "sid:'" + sessionId + "',"
                + "r:'" + path + "',"
                + "clw:'" + connectionLostWidgetHtml + "',"
                + "heartbeatInterval:" + heartBeatInterval
                + "}";
        return new UpgradingPageRenderContext(context, cfg);
    }

    @Override
    public void setStatusCode(final int statusCode) {
        context.setStatusCode(statusCode);
    }

    @Override
    public void setHeaders(final Map<String, String> headers) {
        context.setHeaders(headers);
    }

    @Override
    public void setDocType(final String docType) {
        context.setDocType(docType);
    }

    @Override
    public void openNode(final XmlNs xmlNs, final String name) {
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
    public void closeNode(final String name, final boolean upgrade) {
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
    public void setAttr(final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        context.setAttr(xmlNs, name, value, isProperty);
    }

    @Override
    public void setStyle(final String name, final String value) {
        context.setStyle(name, value);
    }

    @Override
    public void addTextNode(final String text) {
        context.addTextNode(text);
    }

    @Override
    public void addEvent(final Optional<VirtualDomPath> elementPath,
                         final String eventName,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final Event.Modifier modifier) {
       context.addEvent(elementPath, eventName, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addRef(final Ref ref) {
        context.addRef(ref);
    }

    @Override
    public Tag tag() {
        return context.tag();
    }

    @Override
    public PageRenderContext newInstance() {
        return new UpgradingPageRenderContext(context.newInstance(), pageInfo);
    }

    @Override
    public VirtualDomPath rootPath() {
        return context.rootPath();
    }

    @Override
    public Map<Event.Target, Event> events() {
        return context.events();
    }

    @Override
    public Map<Ref, VirtualDomPath> refs() {
        return context.refs();
    }

    @Override
    public String toString() {
        return context.toString();
    }
}
