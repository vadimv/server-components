package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class EnrichingXhtmlContext implements RenderContext {

    private final RenderContext context;
    private final String sessionId;
    private final String path;
    private final String connectionLostWidget;
    private final int heartBeatInterval;

    private boolean headWasOpened = false;

    public EnrichingXhtmlContext(RenderContext context,
                                 String sessionId,
                                 String path,
                                 String connectionLostWidgetHtml,
                                 int heartBeatInterval) {
        this.context = context;
        this.sessionId = sessionId;
        this.path = path;
        this.connectionLostWidget = connectionLostWidgetHtml;
        this.heartBeatInterval = heartBeatInterval;
    }

    public static final BiFunction<String, RenderContext, RenderContext> createFun(int heartbeatIntervalMs) {
        return (sessionId, ctx) -> new EnrichingXhtmlContext(ctx,
                                                             sessionId,
                                                             "/",
                                                             DefaultConnectionLostWidget.HTML,
                                                             heartbeatIntervalMs);
    }

    @Override
    public void openNode(XmlNs xmlNs, String name) {
        if (!headWasOpened && name.equals("body") && xmlNs.equals(XmlNs.html)) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            context.openNode(XmlNs.html, "head");
            upgradeHead();
            context.closeNode("head");
        } else if (xmlNs.equals(XmlNs.html) && name.equals("head")) {
            headWasOpened = true;
        }
        context.openNode(xmlNs, name);
    }

    @Override
    public void closeNode(String name) {
        if (headWasOpened && name.equals("head")) {
            upgradeHead();
        }
        context.closeNode(name);
    }

    private void upgradeHead() {
        final String cfg = "window['kfg']={"
                + "sid:'" + sessionId + "',"
                + "r:'" + path + "',"
                + "clw:'" + connectionLostWidget + "',"
                + "heartbeatInterval:" + heartBeatInterval
                + "}";

        context.openNode(XmlNs.html, "script");
        context.addTextNode(cfg);
        context.closeNode("script");

        context.openNode(XmlNs.html, "script");
        context.setAttr(XmlNs.html, "src", "/static/rsp-client.min.js", false);
        context.setAttr(XmlNs.html, "defer", "defer", true);
        context.closeNode("script");
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
    public void addEvent(Optional<VirtualDomPath> elementPath,
                         String eventName,
                         Consumer<EventContext> eventHandler,
                         boolean preventDefault,
                         Event.Modifier modifier) {
       context.addEvent(elementPath, eventName, eventHandler, preventDefault, modifier);
    }

    @Override
    public void addRef(Ref ref) {
        context.addRef(ref);
    }

    @Override
    public String toString() {
        return context.toString();
    }
}
