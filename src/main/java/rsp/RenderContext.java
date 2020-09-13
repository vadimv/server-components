package rsp;

import rsp.dsl.RefDefinition;

import java.util.function.Consumer;

public interface RenderContext<S> {
    void openNode(XmlNs xmlns, String name);
    void closeNode(String name);
    void setAttr(XmlNs xmlNs, String name, String value);
    void setStyle(String name, String value);
    void addTextNode(String text);
    void addEvent(String eventName, Consumer<EventContext> eventHandler);
    void addRef(RefDefinition ref);
}
