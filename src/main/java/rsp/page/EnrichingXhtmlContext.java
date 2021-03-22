package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;

import java.util.Optional;
import java.util.function.Consumer;

public final class EnrichingXhtmlContext implements RenderContext {

    //public static final String UPGRADABLE_HEAD_SPECIAL_TAG_NAME = "rsp-upgradable-head";

    private final RenderContext context;
    private final String pageInfo;

    private boolean headWasOpened = false;

    private EnrichingXhtmlContext(RenderContext context, String pageInfo) {
        this.context = context;
        this.pageInfo = pageInfo;
    }

    public static EnrichingXhtmlContext create(RenderContext context,
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
        return new EnrichingXhtmlContext(context, cfg);
    }

    @Override
    public void openNode(XmlNs xmlNs, String name) {
        if (!headWasOpened && xmlNs.equals(XmlNs.html) && name.equals("body")) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            context.openNode(XmlNs.html, "head");
            upgradeHead();
            context.closeNode("head", false);
        } else if (xmlNs.equals(XmlNs.html) && name.equals("head")) {
            headWasOpened = true;
        }
        context.openNode(xmlNs, name);
    }

    @Override
    public void closeNode(String name, boolean upgrade) {
        if (headWasOpened && upgrade && name.equals("head")) {
            upgradeHead();
        }
        context.closeNode(name, upgrade);
    }

    private void upgradeHead() {
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
