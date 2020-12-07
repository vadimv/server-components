package rsp.page;

import rsp.dom.Event;
import rsp.dom.Path;
import rsp.dom.XmlNs;
import rsp.dsl.EventDefinition;
import rsp.dsl.Ref;

import java.util.Optional;
import java.util.function.Consumer;

public interface RenderContext {
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name);
    void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(Optional<Path> elementPath,
                  String eventName,
                  Consumer<EventContext> eventHandler,
                  boolean preventDefault,
                  Event.Modifier modifier);
    void addRef(Ref ref);
}
