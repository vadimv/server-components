package rsp;

import rsp.dsl.EventDefinition;
import rsp.dsl.RefDefinition;

import java.util.function.Consumer;

public class EnrichingXhtmlContext implements RenderContext {

    private final RenderContext context;
    private final String sessionId;
    private final String path;
    private final String connectionLostWidget;
    private final int heartBeatInterval;

    private boolean headWasOpened = false;

    public EnrichingXhtmlContext(RenderContext context,
                                 String sessionId,
                                 String path,
                                 String connectionLostWidget,
                                 int heartBeatInterval) {
        this.context = context;
        this.sessionId = sessionId;
        this.path = path;
        this.connectionLostWidget = connectionLostWidget;
        this.heartBeatInterval = heartBeatInterval;
    }

    @Override
    public void openNode(XmlNs xmlNs, String name) {
        if (!headWasOpened && name == "body" && xmlNs == XmlNs.html) {
            // No <head> have opened above
            // it means a programmer didn't include head() in the page
            context.openNode(XmlNs.html, "head");
            upgradeHead();
            context.closeNode("head");
        } else if (xmlNs == XmlNs.html && name == "head") {
            headWasOpened = true;
        }
        context.openNode(xmlNs, name);
    }

    @Override
    public void closeNode(String name) {
        if (headWasOpened && name == "head") {
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
        context.setAttr(XmlNs.html, "src", "/static/korolev-client.min.js");
        context.setAttr(XmlNs.html, "defer", "");
        context.closeNode("script");
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value) {
        context.setAttr(xmlNs, name, value);
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
    public void addEvent(EventDefinition.EventElementMode mode,
                         String eventName,
                         Consumer<EventContext> eventHandler,
                         Event.Modifier modifier) {
       context.addEvent(mode, eventName, eventHandler, modifier);
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
