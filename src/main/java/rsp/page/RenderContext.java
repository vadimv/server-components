package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;
import java.util.function.Consumer;

public interface RenderContext {
    void setDocType(String docType);
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name, boolean upgrade);
    void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(VirtualDomPath elementPath,
                  String eventName,
                  Consumer<EventContext> eventHandler,
                  boolean preventDefault,
                  Event.Modifier modifier);
    void addEvent(String eventName,
                  Consumer<EventContext> eventHandler,
                  boolean preventDefault,
                  Event.Modifier modifier);
    void addRef(Ref ref);
}
