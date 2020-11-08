package rsp;

import rsp.dsl.EventDefinition;

import java.util.function.Consumer;

public interface RenderContext {
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name);
    void setAttr(XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(EventDefinition.EventElementMode mode,
                  String eventName,
                  Consumer<EventContext> eventHandler,
                  Event.Modifier modifier);
    void addRef(Ref ref);
}
