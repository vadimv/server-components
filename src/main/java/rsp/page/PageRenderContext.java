package rsp.page;

import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;
import rsp.ref.Ref;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public interface PageRenderContext {
    void setStatusCode(int statusCode);
    void setHeaders(Map<String, String> headers);
    void setDocType(String docType);
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name, boolean upgrade);
    void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(Optional<VirtualDomPath> elementPath,
                  String eventName,
                  Consumer<EventContext> eventHandler,
                  boolean preventDefault,
                  Event.Modifier modifier);
    void addRef(Ref ref);
    StateNotificationListener getStateNotificationListener();
}
